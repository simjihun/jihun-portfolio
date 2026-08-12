package com.jihun.portfolio.auth.repository;

import com.jihun.portfolio.auth.domain.PasswordResetCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, Long> {

    Optional<PasswordResetCode> findTopByMemberIdAndUsedFalseOrderByCreatedAtDesc(Long memberId);

    Optional<PasswordResetCode> findByResetTokenAndUsedFalse(String resetToken);
}
