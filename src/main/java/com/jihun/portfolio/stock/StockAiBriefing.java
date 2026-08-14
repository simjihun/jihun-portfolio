package com.jihun.portfolio.stock;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * AI 시황 브리핑(요약·주목 종목·이번 주 체크포인트) 스냅샷 — 단일 row만 유지한다(id 고정).
 * 하루 3회(09/15/21시 KST) StockDashboardService의 스케줄러가 Gemini로 새로 만들어 덮어쓰고,
 * 방문자 요청(getAiBriefing)은 이 row를 그대로 읽기만 한다 — Gemini 무료 티어 일일 한도를
 * 방문자 수와 무관하게 하루 3회(또는 재시도 포함 최대 6회) 호출로 고정하기 위함.
 * picks·weekAhead는 구조가 있는 값이라 JSON 문자열로 직렬화해 저장한다(별도 테이블 없이 단순하게).
 */
@Entity
@Table(name = "stock_ai_briefing")
public class StockAiBriefing {

    public static final String ID = "briefing";

    @Id
    @Column(length = 20)
    private String id = ID;

    @Lob
    @Column(nullable = false)
    private String summary;

    /** JSON 배열 문자열 — [{"name":..,"symbol":..,"market":..,"reason":..}, ...] */
    @Lob
    @Column(nullable = false)
    private String picksJson;

    /** JSON 배열 문자열 — ["...", "..."] */
    @Lob
    @Column(nullable = false)
    private String weekAheadJson;

    @Column(nullable = false)
    private long generatedAt;

    protected StockAiBriefing() {}

    public StockAiBriefing(String summary, String picksJson, String weekAheadJson, long generatedAt) {
        this.id = ID;
        this.summary = summary;
        this.picksJson = picksJson;
        this.weekAheadJson = weekAheadJson;
        this.generatedAt = generatedAt;
    }

    public void update(String summary, String picksJson, String weekAheadJson, long generatedAt) {
        this.summary = summary;
        this.picksJson = picksJson;
        this.weekAheadJson = weekAheadJson;
        this.generatedAt = generatedAt;
    }

    public String getId() { return id; }
    public String getSummary() { return summary; }
    public String getPicksJson() { return picksJson; }
    public String getWeekAheadJson() { return weekAheadJson; }
    public long getGeneratedAt() { return generatedAt; }
}
