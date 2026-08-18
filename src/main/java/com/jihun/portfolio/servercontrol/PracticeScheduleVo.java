package com.jihun.portfolio.servercontrol;

import java.time.LocalDateTime;

/**
 * MMS 발송 스케줄 예제 VO — INSERT/UPDATE 연습 전용 테이블.
 */
public class PracticeScheduleVo {

    private Long scheduleId;
    private Long serverId;
    private String scheduleName;
    private String sendTime;
    private String repeatType;
    private String status;
    private LocalDateTime regDate;
    private LocalDateTime updDate;

    public Long getScheduleId() {
        return scheduleId;
    }

    public void setScheduleId(Long scheduleId) {
        this.scheduleId = scheduleId;
    }

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getScheduleName() {
        return scheduleName;
    }

    public void setScheduleName(String scheduleName) {
        this.scheduleName = scheduleName;
    }

    public String getSendTime() {
        return sendTime;
    }

    public void setSendTime(String sendTime) {
        this.sendTime = sendTime;
    }

    public String getRepeatType() {
        return repeatType;
    }

    public void setRepeatType(String repeatType) {
        this.repeatType = repeatType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRegDate() {
        return regDate;
    }

    public void setRegDate(LocalDateTime regDate) {
        this.regDate = regDate;
    }

    public LocalDateTime getUpdDate() {
        return updDate;
    }

    public void setUpdDate(LocalDateTime updDate) {
        this.updDate = updDate;
    }
}
