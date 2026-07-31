package com.jihun.portfolio.message.controller;

import com.jihun.portfolio.message.domain.Message;
import com.jihun.portfolio.message.domain.MessageStatus;
import com.jihun.portfolio.message.queue.MessageQueue;
import com.jihun.portfolio.message.repository.MessageRepository;
import com.jihun.portfolio.message.worker.WorkerStatusRegistry;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 메시지 대시보드용 통계 API.
 * 상태별 건수, 성공률, 대기열, 워커 상태, 최근 30분간 분당 처리량을 한 번에 반환한다.
 */
@RestController
@RequestMapping("/api/message")
public class StatsController {

    private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("HH:mm");

    private final MessageRepository messageRepository;
    private final MessageQueue messageQueue;
    private final WorkerStatusRegistry registry;

    public StatsController(MessageRepository messageRepository,
                           MessageQueue messageQueue,
                           WorkerStatusRegistry registry) {
        this.messageRepository = messageRepository;
        this.messageQueue = messageQueue;
        this.registry = registry;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        long total = messageRepository.count();
        long sent = messageRepository.countByStatus(MessageStatus.SENT);
        long failed = messageRepository.countByStatus(MessageStatus.FAILED);
        long pending = messageRepository.countByStatus(MessageStatus.PENDING);
        long sending = messageRepository.countByStatus(MessageStatus.SENDING);

        double successRate = (sent + failed) == 0
                ? 100.0
                : Math.round(sent * 1000.0 / (sent + failed)) / 10.0;

        // === 최근 30분간 분 단위 버킷 생성 ===
        LocalDateTime from = LocalDateTime.now().minusMinutes(29).withSecond(0).withNano(0);
        Map<String, long[]> buckets = new LinkedHashMap<>(); // 라벨 -> [접수, 성공, 실패]
        for (int i = 0; i < 30; i++) {
            buckets.put(from.plusMinutes(i).format(MINUTE), new long[3]);
        }

        for (Message m : messageRepository.findByCreatedAtAfter(from)) {
            long[] b = buckets.get(m.getCreatedAt().format(MINUTE));
            if (b != null) b[0]++;
        }
        for (Message m : messageRepository.findBySentAtAfter(from)) {
            long[] b = buckets.get(m.getSentAt().format(MINUTE));
            if (b == null) continue;
            if (m.getStatus() == MessageStatus.SENT) b[1]++;
            else if (m.getStatus() == MessageStatus.FAILED) b[2]++;
        }

        List<String> labels = new ArrayList<>(buckets.keySet());
        List<Long> createdSeries = new ArrayList<>();
        List<Long> sentSeries = new ArrayList<>();
        List<Long> failedSeries = new ArrayList<>();
        for (long[] b : buckets.values()) {
            createdSeries.add(b[0]);
            sentSeries.add(b[1]);
            failedSeries.add(b[2]);
        }

        return Map.of(
                "total", total,
                "sent", sent,
                "failed", failed,
                "pending", pending,
                "sending", sending,
                "queueWaiting", messageQueue.size(),
                "successRate", successRate,
                "workers", registry.snapshot(),
                "chart", Map.of(
                        "labels", labels,
                        "created", createdSeries,
                        "sent", sentSeries,
                        "failed", failedSeries
                )
        );
    }
}
