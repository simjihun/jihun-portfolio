package com.jihun.portfolio.servercontrol;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 서버 리소스 지표(CPU/MEM/DISK) 1건 VO. 실시간 차트에 그대로 뿌려진다.
 */
public class ServerMetricVo {

    private Long metricId;
    private Long serverId;
    private BigDecimal cpuUsage;
    private BigDecimal memUsage;
    private BigDecimal diskUsage;
    private LocalDateTime checkedAt;

    public Long getMetricId() {
        return metricId;
    }

    public void setMetricId(Long metricId) {
        this.metricId = metricId;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public BigDecimal getCpuUsage() {
        return cpuUsage;
    }

    public void setCpuUsage(BigDecimal cpuUsage) {
        this.cpuUsage = cpuUsage;
    }

    public BigDecimal getMemUsage() {
        return memUsage;
    }

    public void setMemUsage(BigDecimal memUsage) {
        this.memUsage = memUsage;
    }

    public BigDecimal getDiskUsage() {
        return diskUsage;
    }

    public void setDiskUsage(BigDecimal diskUsage) {
        this.diskUsage = diskUsage;
    }

    public LocalDateTime getCheckedAt() {
        return checkedAt;
    }

    public void setCheckedAt(LocalDateTime checkedAt) {
        this.checkedAt = checkedAt;
    }
}
