package com.jihun.portfolio.message.worker;

import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 워커 상태 실시간 레지스트리.
 *
 * 각 워커 쓰레드가 지금 놀고 있는지(IDLE), 무엇을 보내는 중인지(SENDING)를
 * 기록해서 대시보드에서 실시간으로 보여준다.
 * 실제 SMS 게이트웨이 관제 콘솔의 "채널 모니터링"과 같은 개념.
 */
@Component
public class WorkerStatusRegistry {

    /** 워커 1개의 현재 상태 */
    public record WorkerState(String worker, String status, Long messageId, String receiver) {}

    private final Map<String, WorkerState> states = new ConcurrentHashMap<>();

    public void idle(String worker) {
        states.put(worker, new WorkerState(worker, "IDLE", null, null));
    }

    public void sending(String worker, Long messageId, String receiver) {
        states.put(worker, new WorkerState(worker, "SENDING", messageId, receiver));
    }

    public List<WorkerState> snapshot() {
        return states.values().stream()
                .sorted(Comparator.comparing(WorkerState::worker))
                .toList();
    }
}
