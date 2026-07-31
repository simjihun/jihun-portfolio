package com.jihun.portfolio.message.repository;

import com.jihun.portfolio.message.domain.Message;
import com.jihun.portfolio.message.domain.MessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Spring Data JPA가 메서드 이름을 해석해 구현체를 자동 생성한다.
 */
public interface MessageRepository extends JpaRepository<Message, Long> {

    // --- 통계용 ---
    long countByStatus(MessageStatus status);
    List<Message> findByCreatedAtAfter(LocalDateTime from);
    List<Message> findBySentAtAfter(LocalDateTime from);

    // --- 이력 조회 (필터 조합별, 페이징) ---
    Page<Message> findAllByOrderByIdDesc(Pageable pageable);
    Page<Message> findByStatusOrderByIdDesc(MessageStatus status, Pageable pageable);
    Page<Message> findByReceiverContainingOrderByIdDesc(String receiver, Pageable pageable);
    Page<Message> findByStatusAndReceiverContainingOrderByIdDesc(MessageStatus status, String receiver, Pageable pageable);
}
