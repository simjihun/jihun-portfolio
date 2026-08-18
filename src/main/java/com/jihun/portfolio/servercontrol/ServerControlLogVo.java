package com.jihun.portfolio.servercontrol;

import java.time.LocalDateTime;

/**
 * 서버 시작/중지/재시작 제어 이력 1건 VO.
 */
public class ServerControlLogVo {

    private Long logId;
    private Long serverId;
    private String actionType;   // START, STOP, RESTART
    private String actionResult; // SUCCESS, FAIL
    private String requestedBy;
    private LocalDateTime requestedAt;

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getActionType() {
        return actionType;
    }

    public void setActionType(String actionType) {
        this.actionType = actionType;
    }

    public String getActionResult() {
        return actionResult;
    }

    public void setActionResult(String actionResult) {
        this.actionResult = actionResult;
    }

    public String getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }
}
