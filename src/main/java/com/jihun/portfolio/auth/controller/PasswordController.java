package com.jihun.portfolio.auth.controller;

import com.jihun.portfolio.auth.service.MemberService;
import com.jihun.portfolio.auth.service.PasswordResetService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth/password")
public class PasswordController {

    private final MemberService memberService;
    private final PasswordResetService resetService;

    public PasswordController(MemberService memberService, PasswordResetService resetService) {
        this.memberService = memberService;
        this.resetService = resetService;
    }

    public record ChangeRequest(String currentPassword, String newPassword) {}
    public record ForgotRequest(String username, String channel, String contact) {}
    public record VerifyRequest(String username, String code) {}
    public record ResetRequest(String resetToken, String newPassword) {}

    /** 로그인한 상태에서 현재 비밀번호 확인 후 변경 */
    @PostMapping("/change")
    public Map<String, Object> change(Authentication auth, @RequestBody ChangeRequest req) {
        return memberService.changePassword(auth.getName(), req.currentPassword(), req.newPassword());
    }

    /** 로그인 못하는 상태 — 아이디+이메일(또는 SMS, 준비중)로 인증번호 요청 */
    @PostMapping("/forgot")
    public Map<String, Object> forgot(@RequestBody ForgotRequest req) {
        return resetService.requestCode(req.username(), req.channel(), req.contact());
    }

    /** 인증번호 확인 → 성공 시 1회용 resetToken 발급 */
    @PostMapping("/verify")
    public Map<String, Object> verify(@RequestBody VerifyRequest req) {
        return resetService.verifyCode(req.username(), req.code());
    }

    /** resetToken으로 새 비밀번호 저장 */
    @PostMapping("/reset")
    public Map<String, Object> reset(@RequestBody ResetRequest req) {
        return resetService.resetPassword(req.resetToken(), req.newPassword());
    }
}
