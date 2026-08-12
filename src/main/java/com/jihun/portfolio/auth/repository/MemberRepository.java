package com.jihun.portfolio.auth.repository;

import com.jihun.portfolio.auth.domain.Member;
import com.jihun.portfolio.auth.domain.MemberStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MemberRepository extends JpaRepository<Member, Long> {

    Optional<Member> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmailLookupHash(String emailLookupHash);

    boolean existsByPhoneLookupHash(String phoneLookupHash);

    List<Member> findByStatusOrderByCreatedAtAsc(MemberStatus status);

    List<Member> findAllByOrderByCreatedAtDesc();
}
