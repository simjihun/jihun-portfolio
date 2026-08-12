package com.jihun.portfolio.auth.controller;

import com.jihun.portfolio.auth.domain.MemberStatus;
import com.jihun.portfolio.auth.service.MemberService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 관리자 전용 회원 관리 API. SecurityConfig에서 /api/admin/** 은 ROLE_ADMIN만 접근 가능하도록 막아둠. */
@RestController
@RequestMapping("/api/admin/members")
public class AdminMemberController {

    private final MemberService memberService;

    public AdminMemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) MemberStatus status) {
        return memberService.listMembers(status);
    }

    @PostMapping("/{id}/approve")
    public Map<String, Object> approve(@PathVariable Long id) {
        return Map.of("success", memberService.updateStatus(id, MemberStatus.APPROVED));
    }

    @PostMapping("/{id}/reject")
    public Map<String, Object> reject(@PathVariable Long id) {
        return Map.of("success", memberService.updateStatus(id, MemberStatus.REJECTED));
    }
}
