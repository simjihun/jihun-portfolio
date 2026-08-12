package com.jihun.portfolio.auth.controller;

import com.jihun.portfolio.auth.domain.Member;
import com.jihun.portfolio.auth.service.MemberService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final MemberService memberService;

    public AuthController(MemberService memberService) {
        this.memberService = memberService;
    }

    public record SignupRequest(String username, String password, String name, String phone, String email) {}

    @PostMapping("/signup")
    public Map<String, Object> signup(@RequestBody SignupRequest req) {
        return memberService.signup(req.username(), req.password(), req.name(), req.phone(), req.email());
    }

    /** 로그인 상태·이름·권한 확인용(마이페이지/관리자 화면 진입 시 프론트에서 조회). */
    @GetMapping("/me")
    public Map<String, Object> me(Authentication auth) {
        Map<String, Object> res = new HashMap<>();
        if (auth == null || !auth.isAuthenticated() || "anonymousUser".equals(auth.getPrincipal())) {
            res.put("authenticated", false);
            return res;
        }
        res.put("authenticated", true);
        res.put("username", auth.getName());
        res.put("roles", auth.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList());
        memberService.findByUsername(auth.getName()).ifPresent(m -> res.put("name", m.getName()));
        return res;
    }
}
