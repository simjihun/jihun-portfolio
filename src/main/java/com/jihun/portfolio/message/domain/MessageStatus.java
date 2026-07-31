package com.jihun.portfolio.message.domain;

/**
 * 메시지의 생명주기 상태.
 * 실제 문자 발송 시스템도 이런 상태 전이(접수 → 발송중 → 성공/실패)를 가진다.
 */
public enum MessageStatus {
    PENDING,   // 접수됨 (큐에서 대기 중)
    SENDING,   // 워커가 꺼내서 발송 처리 중
    SENT,      // 발송 성공
    FAILED     // 발송 실패
}
