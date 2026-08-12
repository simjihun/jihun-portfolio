package com.jihun.portfolio.auth.service;

import com.jihun.portfolio.auth.domain.Member;
import com.jihun.portfolio.auth.domain.PasswordResetCode;
import com.jihun.portfolio.auth.repository.PasswordResetCodeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 비밀번호 찾기(재설정) 3단계.
 * 1) requestCode: 아이디+이메일이 실제로 일치할 때만 인증번호를 만들어 발송(존재 여부를 응답으로 노출하지 않음)
 * 2) verifyCode: 인증번호가 유효하면 1회용 resetToken 발급 — 이 뒤로는 인증번호 자체는 더 쓸 수 없음
 * 3) resetPassword: resetToken으로만 실제 비밀번호를 바꿈
 *
 * SMS 채널은 UI·API 형태는 준비돼 있지만 실제 발송 연동 전이라 안내 메시지만 돌려준다(추후 확장 지점).
 */
@Service
public class PasswordResetService {

    private static final int CODE_EXPIRY_MINUTES = 5;
    private static final int TOKEN_EXPIRY_MINUTES = 15;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberService memberService;
    private final CryptoService crypto;
    private final EmailService emailService;
    private final PasswordResetCodeRepository codeRepository;
    private final PasswordEncoder passwordEncoder;

    public PasswordResetService(MemberService memberService, CryptoService crypto, EmailService emailService,
                                 PasswordResetCodeRepository codeRepository, PasswordEncoder passwordEncoder) {
        this.memberService = memberService;
        this.crypto = crypto;
        this.emailService = emailService;
        this.codeRepository = codeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Map<String, Object> requestCode(String username, String channel, String contact) {
        if (!"EMAIL".equalsIgnoreCase(channel)) {
            return Map.of("success", false, "message", "SMS 인증은 아직 준비 중입니다. 이메일 인증을 이용해주세요.");
        }
        if (username == null || contact == null || contact.isBlank()) {
            return Map.of("success", false, "message", "아이디와 이메일을 모두 입력해주세요");
        }
        Optional<Member> memberOpt = memberService.findByUsername(username);
        // 실제로 아이디+이메일이 일치할 때만 발송하되, 응답 메시지는 항상 동일하게 돌려줘서
        // "이 아이디가 존재하는지/이메일이 맞는지"를 외부에서 추측할 수 없게 한다.
        if (memberOpt.isPresent()) {
            Member m = memberOpt.get();
            if (crypto.lookupHash(contact).equals(m.getEmailLookupHash())) {
                String code = String.format("%06d", RANDOM.nextInt(1_000_000));
                codeRepository.save(new PasswordResetCode(m.getId(), code, "EMAIL",
                        LocalDateTime.now().plusMinutes(CODE_EXPIRY_MINUTES)));
                emailService.sendVerificationCode(contact, code);
            }
        }
        return Map.of("success", true, "message", "입력하신 정보가 일치하면 인증번호가 이메일로 발송됩니다. (5분간 유효)");
    }

    @Transactional
    public Map<String, Object> verifyCode(String username, String code) {
        if (username == null || code == null) return Map.of("success", false, "message", "인증번호가 일치하지 않습니다");
        Optional<Member> memberOpt = memberService.findByUsername(username);
        if (memberOpt.isEmpty()) return Map.of("success", false, "message", "인증번호가 일치하지 않습니다");
        Member m = memberOpt.get();
        Optional<PasswordResetCode> codeOpt = codeRepository.findTopByMemberIdAndUsedFalseOrderByCreatedAtDesc(m.getId());
        if (codeOpt.isEmpty()) return Map.of("success", false, "message", "발급된 인증번호가 없습니다. 다시 요청해주세요");
        PasswordResetCode entry = codeOpt.get();
        if (entry.getExpiresAt().isBefore(LocalDateTime.now()))
            return Map.of("success", false, "message", "인증번호가 만료되었습니다. 다시 요청해주세요");
        if (!entry.getCode().equals(code))
            return Map.of("success", false, "message", "인증번호가 일치하지 않습니다");
        entry.setVerified(true);
        entry.setResetToken(UUID.randomUUID().toString());
        codeRepository.save(entry);
        return Map.of("success", true, "resetToken", entry.getResetToken());
    }

    @Transactional
    public Map<String, Object> resetPassword(String resetToken, String newPassword) {
        if (resetToken == null) return Map.of("success", false, "message", "인증이 만료되었거나 유효하지 않습니다. 처음부터 다시 시도해주세요");
        Optional<PasswordResetCode> entryOpt = codeRepository.findByResetTokenAndUsedFalse(resetToken);
        if (entryOpt.isEmpty()) return Map.of("success", false, "message", "인증이 만료되었거나 유효하지 않습니다. 처음부터 다시 시도해주세요");
        PasswordResetCode entry = entryOpt.get();
        if (!entry.isVerified() || entry.getCreatedAt().plusMinutes(TOKEN_EXPIRY_MINUTES).isBefore(LocalDateTime.now()))
            return Map.of("success", false, "message", "인증이 만료되었습니다. 처음부터 다시 시도해주세요");
        if (newPassword == null || newPassword.length() < 8)
            return Map.of("success", false, "message", "새 비밀번호는 8자 이상이어야 합니다");

        boolean ok = memberService.resetPasswordByMemberId(entry.getMemberId(), passwordEncoder.encode(newPassword));
        if (!ok) return Map.of("success", false, "message", "사용자를 찾을 수 없습니다");
        entry.setUsed(true);
        codeRepository.save(entry);
        return Map.of("success", true, "message", "비밀번호가 재설정되었습니다. 새 비밀번호로 로그인해주세요.");
    }
}
