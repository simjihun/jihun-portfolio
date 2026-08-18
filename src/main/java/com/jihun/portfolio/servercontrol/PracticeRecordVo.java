package com.jihun.portfolio.servercontrol;

import java.time.LocalDateTime;

/**
 * DB CRUD(select/insert/update/delete) 연습용 더미 테이블 VO.
 * 컨트롤러에서 폼 파라미터를 이 VO로 바로 바인딩받아 사용한다(별도 @ModelAttribute
 * 표기 없이도 Spring MVC가 비-원시타입 파라미터는 자동으로 프로퍼티 바인딩을 시도한다
 * — 10년 전 프로젝트에서 자주 보이는, 명시적 표기를 생략한 암묵적 바인딩 스타일).
 */
public class PracticeRecordVo {

    private Long recordId;
    private String recordName;
    private String recordEmail;
    private String recordStatus; // ACTIVE, INACTIVE
    private LocalDateTime regDate;
    private LocalDateTime updDate;

    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getRecordName() {
        return recordName;
    }

    public void setRecordName(String recordName) {
        this.recordName = recordName;
    }

    public String getRecordEmail() {
        return recordEmail;
    }

    public void setRecordEmail(String recordEmail) {
        this.recordEmail = recordEmail;
    }

    public String getRecordStatus() {
        return recordStatus;
    }

    public void setRecordStatus(String recordStatus) {
        this.recordStatus = recordStatus;
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
