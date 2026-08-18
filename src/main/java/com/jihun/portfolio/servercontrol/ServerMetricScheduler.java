package com.jihun.portfolio.servercontrol;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Random;

/**
 * 실제 서버가 없으므로, 등록된 서버마다 주기적으로 CPU/메모리/디스크 사용률을
 * 임의로 만들어 admin_server_metric에 쌓는다. 관리자 페이지의 실시간(폴링) 차트가
 * 5초마다 /api/servercontrol/metrics를 조회해서 그린다.
 *
 * 완전 랜덤 값 대신 직전 값에서 조금씩 흔들리는 "랜덤워크" 방식을 써서, 그래프가
 * 실제 서버 부하처럼 자연스럽게 등락하도록 했다.
 */
@Component
public class ServerMetricScheduler {

    @Autowired
    private ServerControlDao serverControlDao;

    private final Random random = new Random();

    @Scheduled(fixedRate = 10000, initialDelay = 3000)
    public void collectMetrics() {
        List<ServerVo> servers = serverControlDao.selectServerList();
        for (ServerVo server : servers) {
            if (!"RUNNING".equals(server.getStatus())) {
                continue; // 중지된 서버는 지표를 수집하지 않는다(실제 운영과 동일한 감각)
            }

            ServerMetricVo prev = serverControlDao.selectLatestMetric(server.getServerId());

            double prevCpu = prev == null ? 30 : prev.getCpuUsage().doubleValue();
            double prevMem = prev == null ? 45 : prev.getMemUsage().doubleValue();
            double prevDisk = prev == null ? 55 : prev.getDiskUsage().doubleValue();

            double cpu = randomWalk(prevCpu, 8, 10, 95);
            double mem = randomWalk(prevMem, 4, 15, 90);
            double disk = randomWalk(prevDisk, 0.5, 20, 85); // 디스크는 거의 안 움직이게

            ServerMetricVo vo = new ServerMetricVo();
            vo.setServerId(server.getServerId());
            vo.setCpuUsage(round2(cpu));
            vo.setMemUsage(round2(mem));
            vo.setDiskUsage(round2(disk));
            serverControlDao.insertMetric(vo);
        }
    }

    private double randomWalk(double current, double maxDelta, double min, double max) {
        double next = current + (random.nextDouble() * 2 - 1) * maxDelta;
        if (next < min) {
            next = min;
        }
        if (next > max) {
            next = max;
        }
        return next;
    }

    private BigDecimal round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP);
    }
}
