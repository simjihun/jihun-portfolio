package com.jihun.portfolio.auth.service;

import com.jihun.portfolio.auth.domain.Member;
import com.jihun.portfolio.auth.domain.MemberStatus;
import com.jihun.portfolio.auth.repository.MemberRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Spring Security 인증에 회원 데이터를 연결.
 * status가 APPROVED가 아니면 disabled/locked로 처리해, PENDING(승인 대기)·REJECTED(거절)
 * 상태의 계정은 비밀번호가 맞아도 로그인이 되지 않게 한다.
 */
@Service
public class MemberDetailsService implements UserDetailsService {

    private final MemberRepository memberRepository;

    public MemberDetailsService(MemberRepository memberRepository) {
        this.memberRepository = memberRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) {
        Member m = memberRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("존재하지 않는 아이디입니다"));
        return User.builder()
                .username(m.getUsername())
                .password(m.getPasswordHash())
                .disabled(m.getStatus() == MemberStatus.PENDING)
                .accountLocked(m.getStatus() == MemberStatus.REJECTED)
                .authorities("ROLE_" + m.getRole().name())
                .build();
    }
}
