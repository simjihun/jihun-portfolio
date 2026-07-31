package com.jihun.portfolio.message.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 발송 메시지 1건을 나타내는 엔티티.
 * 테이블명을 message로 고정해 기존 RDS 데이터와 호환을 유지한다.
 */
@Entity
@Table(name = "message")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String receiver;          // 수신 번호

    @Column(nullable = false, length = 500)
    private String content;           // 메시지 내용

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MessageStatus status;     // 현재 상태

    @Column(nullable = false)
    private LocalDateTime createdAt;  // 접수 시각

    private LocalDateTime sentAt;     // 발송 완료 시각

    private String workerName;        // 어떤 워커 쓰레드가 처리했는지

    @Column(nullable = false)
    private int retryCount;           // 재시도 횟수

    protected Message() {
        // JPA가 사용하는 기본 생성자
    }

    public Message(String receiver, String content) {
        this.receiver = receiver;
        this.content = content;
        this.status = MessageStatus.PENDING;
        this.createdAt = LocalDateTime.now();
        this.retryCount = 0;
    }

    /** 워커가 발송 처리를 시작할 때 호출 */
    public void markSending(String workerName) {
        this.status = MessageStatus.SENDING;
        this.workerName = workerName;
    }

    /** 발송 성공 */
    public void markSent() {
        this.status = MessageStatus.SENT;
        this.sentAt = LocalDateTime.now();
    }

    /** 발송 실패 확정 (재시도 횟수 소진) */
    public void markFailed() {
        this.status = MessageStatus.FAILED;
        this.sentAt = LocalDateTime.now();
    }

    /** 자동 재시도 가능 여부 */
    public boolean canRetry(int maxRetry) {
        return retryCount < maxRetry;
    }

    /** 자동 재시도 준비: 횟수 증가 후 다시 대기 상태로 */
    public void prepareRetry() {
        this.retryCount++;
        this.status = MessageStatus.PENDING;
        this.workerName = null;
    }

    /** 수동 재발송: 모든 상태를 초기화하고 처음부터 다시 */
    public void resetForResend() {
        this.retryCount = 0;
        this.status = MessageStatus.PENDING;
        this.sentAt = null;
        this.workerName = null;
    }

    // --- Getter (화면/API 응답용) ---
    public Long getId() { return id; }
    public String getReceiver() { return receiver; }
    public String getContent() { return content; }
    public MessageStatus getStatus() { return status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getSentAt() { return sentAt; }
    public String getWorkerName() { return workerName; }
    public int getRetryCount() { return retryCount; }
}
