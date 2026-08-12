package com.jihun.portfolio.auth.service;

import com.jihun.portfolio.auth.domain.Member;
import com.jihun.portfolio.auth.domain.MemberRole;
import com.jihun.portfolio.auth.domain.MemberStatus;
import com.jihun.portfolio.auth.repository.MemberRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * 회원 가입·조회·승인·비밀번호 변경.
 * 비밀번호는 항상 PasswordEncoder(BCrypt)로 해시해서만 저장한다 — 평문은 메모리에서도 최소한만 다룬다.
 */
@Service
public class MemberService {

    private static final Logger log = LoggerFactory.getLogger(MemberService.class);

    private static final Pattern USERNAME_PATTERN = Pattern.compile("^[a-zA-Z0-9_]{4,20}$");
    private static final Pattern PHONE_PATTERN = Pattern.compile("^01[016789]-?\\d{3,4}-?\\d{4}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final MemberRepository memberRepository;
    private final CryptoService crypto;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.admin.username:}")
    private String bootstrapAdminUsername;
    @Value("${app.admin.password:}")
    private String bootstrapAdminPassword;

    public MemberService(MemberRepository memberRepository, CryptoService crypto, PasswordEncoder passwordEncoder) {
        this.memberRepository = memberRepository;
        this.crypto = crypto;
        this.passwordEncoder = passwordEncoder;
    }

    /** 서버 시작 시 관리자 계정이 하나도 없고 ADMIN_USERNAME/ADMIN_PASSWORD가 설정돼 있으면 1회 생성한다(최초 로그인용). */
    @PostConstruct
    public void bootstrapAdmin() {
        if (bootstrapAdminUsername == null || bootstrapAdminUsername.isBlank()) return;
        if (bootstrapAdminPassword == null || bootstrapAdminPassword.isBlank()) return;
        if (memberRepository.existsByUsername(bootstrapAdminUsername)) return;
        String placeholderPhone = "010-0000-0000";
        String placeholderEmail = bootstrapAdminUsername + "@admin.local";
        Member admin = new Member(
                bootstrapAdminUsername,
                passwordEncoder.encode(bootstrapAdminPassword),
                "관리자",
                crypto.encrypt(placeholderPhone), crypto.lookupHash(placeholderPhone),
                crypto.encrypt(placeholderEmail), crypto.lookupHash(placeholderEmail),
                MemberRole.ADMIN
        );
        admin.setStatus(MemberStatus.APPROVED);
        memberRepository.save(admin);
        log.info("[auth] 최초 관리자 계정 생성 완료: {}", bootstrapAdminUsername);
    }

    public Map<String, Object> signup(String username, String rawPassword, String name, String phone, String email) {
        if (username == null || !USERNAME_PATTERN.matcher(username).matches())
            return fail("아이디는 영문/숫자/밑줄 4~20자로 입력해주세요");
        if (rawPassword == null || rawPassword.length() < 8)
            return fail("비밀번호는 8자 이상이어야 합니다");
        if (name == null || name.isBlank())
            return fail("이름을 입력해주세요");
        if (phone == null || !PHONE_PATTERN.matcher(phone).matches())
            return fail("휴대폰 번호 형식이 올바르지 않습니다 (예: 010-1234-5678)");
        if (email == null || !EMAIL_PATTERN.matcher(email).matches())
            return fail("이메일 형식이 올바르지 않습니다");
        if (memberRepository.existsByUsername(username))
            return fail("이미 사용 중인 아이디입니다");

        String phoneHash = crypto.lookupHash(phone);
        String emailHash = crypto.lookupHash(email);
        if (memberRepository.existsByEmailLookupHash(emailHash))
            return fail("이미 가입에 사용된 이메일입니다");
        if (memberRepository.existsByPhoneLookupHash(phoneHash))
            return fail("이미 가입에 사용된 휴대폰 번호입니다");

        Member member = new Member(
                username, passwordEncoder.encode(rawPassword), name,
                crypto.encrypt(phone), phoneHash,
                crypto.encrypt(email), emailHash,
                MemberRole.USER
        );
        memberRepository.save(member);
        return Map.of("success", true, "message", "가입 신청이 접수되었습니다. 관리자 승인 후 로그인할 수 있습니다.");
    }

    private Map<String, Object> fail(String message) {
        return Map.of("success", false, "message", message);
    }

    public List<Map<String, Object>> listMembers(MemberStatus status) {
        List<Member> list = status == null
                ? memberRepository.findAllByOrderByCreatedAtDesc()
                : memberRepository.findByStatusOrderByCreatedAtAsc(status);
        return list.stream().map(this::toSummary).toList();
    }

    private Map<String, Object> toSummary(Member m) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", m.getId());
        map.put("username", m.getUsername());
        map.put("name", m.getName());
        map.put("phone", crypto.decrypt(m.getPhoneEncrypted()));
        map.put("email", crypto.decrypt(m.getEmailEncrypted()));
        map.put("role", m.getRole());
        map.put("status", m.getStatus());
        map.put("createdAt", m.getCreatedAt());
        return map;
    }

    public boolean updateStatus(Long memberId, MemberStatus status) {
        return memberRepository.findById(memberId).map(m -> {
            m.setStatus(status);
            memberRepository.save(m);
            return true;
        }).orElse(false);
    }

    public Map<String, Object> changePassword(String username, String currentPassword, String newPassword) {
        Member m = memberRepository.findByUsername(username).orElse(null);
        if (m == null) return fail("사용자를 찾을 수 없습니다");
        if (!passwordEncoder.matches(currentPassword == null ? "" : currentPassword, m.getPasswordHash()))
            return fail("현재 비밀번호가 일치하지 않습니다");
        if (newPassword == null || newPassword.length() < 8)
            return fail("새 비밀번호는 8자 이상이어야 합니다");
        m.setPasswordHash(passwordEncoder.encode(newPassword));
        memberRepository.save(m);
        return Map.of("success", true, "message", "비밀번호가 변경되었습니다");
    }

    public boolean resetPasswordByMemberId(Long memberId, String encodedPassword) {
        return memberRepository.findById(memberId).map(m -> {
            m.setPasswordHash(encodedPassword);
            memberRepository.save(m);
            return true;
        }).orElse(false);
    }

    public Optional<Member> findByUsername(String username) {
        return memberRepository.findByUsername(username);
    }
}
