package com.jihun.portfolio.message.worker;

import com.jihun.portfolio.message.domain.Message;
import com.jihun.portfolio.message.queue.MessageQueue;
import com.jihun.portfolio.message.repository.MessageRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * 멀티쓰레드 발송 워커 (데몬).
 *
 * 앱이 시작되면 워커 쓰레드 N개가 백그라운드에서 무한 루프를 돌며
 * 큐에서 메시지를 꺼내 발송 처리한다.
 * 발송 실패 시 최대 max-retry회까지 자동 재시도한다. (실무 메시징 핵심 로직)
 */
@Component
public class MessageSendWorker {

    private static final Logger log = LoggerFactory.getLogger(MessageSendWorker.class);

    private final MessageQueue messageQueue;
    private final MessageRepository messageRepository;
    private final WorkerStatusRegistry registry;

    @Value("${app.worker.count}")
    private int workerCount;

    @Value("${app.worker.send-delay-ms}")
    private long sendDelayMs;

    @Value("${app.worker.max-retry}")
    private int maxRetry;

    private ExecutorService executor;

    private volatile boolean running = true;

    public MessageSendWorker(MessageQueue messageQueue,
                             MessageRepository messageRepository,
                             WorkerStatusRegistry registry) {
        this.messageQueue = messageQueue;
        this.messageRepository = messageRepository;
        this.registry = registry;
    }

    @PostConstruct
    public void start() {
        executor = Executors.newFixedThreadPool(workerCount);
        for (int i = 1; i <= workerCount; i++) {
            String workerName = "worker-" + i;
            registry.idle(workerName);
            executor.submit(() -> runLoop(workerName));
        }
        log.info("메시지 발송 워커 {}개 시작 (최대 재시도 {}회)", workerCount, maxRetry);
    }

    private void runLoop(String workerName) {
        Thread.currentThread().setName(workerName);
        log.info("[{}] 발송 루프 시작", workerName);

        while (running) {
            try {
                Long messageId = messageQueue.poll(1, TimeUnit.SECONDS);
                if (messageId == null) {
                    continue;
                }
                process(workerName, messageId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("[{}] 처리 중 예외 발생", workerName, e);
            }
        }
        log.info("[{}] 발송 루프 종료", workerName);
    }

    /** 메시지 1건 발송 처리 */
    private void process(String workerName, Long messageId) throws InterruptedException {
        Message message = messageRepository.findById(messageId).orElse(null);
        if (message == null) {
            log.warn("[{}] 메시지를 찾을 수 없음 id={}", workerName, messageId);
            return;
        }

        registry.sending(workerName, messageId, message.getReceiver());
        try {
            message.markSending(workerName);
            messageRepository.save(message);
            log.info("[{}] 발송 시작 id={} to={} (시도 {}회차)",
                    workerName, messageId, message.getReceiver(), message.getRetryCount() + 1);

            // === 발송 시뮬레이션 (실제라면 SMPP 소켓 통신 구간) ===
            Thread.sleep(sendDelayMs);

            boolean success = ThreadLocalRandom.current().nextInt(100) < 90;
            if (success) {
                message.markSent();
                messageRepository.save(message);
                log.info("[{}] 발송 성공 id={}", workerName, messageId);
            } else if (message.canRetry(maxRetry)) {
                message.prepareRetry();
                messageRepository.save(message);
                messageQueue.enqueue(messageId);
                log.warn("[{}] 발송 실패 → 재시도 예약 id={} ({}회차 재시도)",
                        workerName, messageId, message.getRetryCount());
            } else {
                message.markFailed();
                messageRepository.save(message);
                log.warn("[{}] 발송 최종 실패 id={} (재시도 {}회 소진)",
                        workerName, messageId, message.getRetryCount());
            }
        } finally {
            registry.idle(workerName);
        }
    }

    @PreDestroy
    public void stop() throws InterruptedException {
        log.info("워커 종료 신호 전송");
        running = false;
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
        log.info("워커 전체 종료 완료");
    }
}
