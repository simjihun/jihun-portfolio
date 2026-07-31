package com.jihun.portfolio.message.queue;

import org.springframework.stereotype.Component;

import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 발송 대기열.
 *
 * 지금은 JVM 안에서만 동작하는 인메모리 큐지만,
 * 이 클래스의 내부 구현만 AWS SQS 호출로 바꾸면
 * 나머지 코드는 손대지 않고 진짜 클라우드 메시징 시스템이 된다.
 */
@Component
public class MessageQueue {

    // BlockingQueue: 여러 쓰레드가 동시에 넣고 빼도 안전한(thread-safe) 큐
    private final LinkedBlockingQueue<Long> queue = new LinkedBlockingQueue<>();

    /** 메시지 ID를 대기열에 넣는다 (API 쓰레드가 호출) */
    public void enqueue(Long messageId) {
        queue.offer(messageId);
    }

    /**
     * 대기열에서 메시지 ID를 하나 꺼낸다 (워커 쓰레드가 호출).
     * 큐가 비어 있으면 최대 timeout 동안 기다렸다가 null 반환.
     */
    public Long poll(long timeout, TimeUnit unit) throws InterruptedException {
        return queue.poll(timeout, unit);
    }

    /** 현재 대기 중인 건수 */
    public int size() {
        return queue.size();
    }
}
