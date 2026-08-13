package com.jihun.portfolio.stock;

import com.jihun.portfolio.portfolio.TossPortfolioService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 주식 대시보드 REST API. 모든 응답은 서버 측 TTL 캐시를 거친다(외부 API·프리티어 보호).
 * 캘린더는 자주 바뀌지 않아 /dashboard의 짧은 폴링 주기와 분리된 별도 엔드포인트로 뺐다
 * (프론트에서 12시간 간격으로만 호출 — 체감상 자주 새로고침되는 느낌을 없애기 위함).
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockDashboardService service;
    private final TossPortfolioService portfolioService;

    public StockController(StockDashboardService service, TossPortfolioService portfolioService) {
        this.service = service;
        this.portfolioService = portfolioService;
    }

    /** 상단 지표 + 수급 (짧은 주기로 폴링되는 부분만) */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> res = new HashMap<>();
        res.put("indicators", service.getIndicators());
        res.put("investorTrading", service.getInvestorTrading());
        return res;
    }

    /** 장 운영 캘린더 (서버 12시간 캐시 — 프론트도 자주 호출하지 않는다) */
    @GetMapping("/calendar")
    public Map<String, Object> calendar() {
        return service.getCalendar();
    }

    @GetMapping("/rankings")
    public Map<String, Object> rankings(@RequestParam(defaultValue = "KR") String country,
                                        @RequestParam(defaultValue = "amount") String tab) {
        return service.getRankings(country, tab);
    }

    @GetMapping("/briefing")
    public Map<String, Object> briefing() {
        return service.getAiBriefing();
    }

    /** 종목 하나에 대한 온디맨드 AI 요약 — 클릭할 때만 생성(2시간 캐시)해 Gemini 호출량을 아낀다. */
    @GetMapping("/insight")
    public Map<String, Object> insight(@RequestParam String symbol,
                                       @RequestParam(defaultValue = "KR") String country) {
        return service.getStockInsight(symbol, country);
    }

    /*
     * ===== 종목 상세 팝업(차트·호가·수급·공매도) =====
     * /admin/toss-stock(로그인 필요)에서 쓰는 TossPortfolioService의 시세 조회 메서드를 그대로
     * 재사용한다 — 계좌 조회가 아니라 종목 시세 자체는 로그인과 무관한 공개 데이터라 인증을 요구하지
     * 않는다. TossPortfolioService 쪽에 짧은 TTL 캐시가 이미 걸려있어(차트 15초·호가 8초 등)
     * 이 페이지를 여러 명이 동시에 봐도 토스 API가 매번 호출되지 않는다.
     */
    @GetMapping("/{symbol}/chart")
    public Map<String, Object> stockChart(@PathVariable String symbol,
                                          @RequestParam(defaultValue = "1d") String interval,
                                          @RequestParam(defaultValue = "200") int count) {
        return portfolioService.getChart(symbol, interval, count);
    }

    @GetMapping("/{symbol}/orderbook")
    public Map<String, Object> stockOrderbook(@PathVariable String symbol) {
        return portfolioService.getOrderbook(symbol);
    }

    @GetMapping("/{symbol}/investor-trading")
    public Map<String, Object> stockInvestorTrading(@PathVariable String symbol,
                                                     @RequestParam(defaultValue = "7") int count) {
        return portfolioService.getInvestorTrading(symbol, count);
    }

    @GetMapping("/{symbol}/short-selling")
    public Map<String, Object> stockShortSelling(@PathVariable String symbol,
                                                  @RequestParam(defaultValue = "7") int count) {
        return portfolioService.getShortSelling(symbol, count);
    }

    @GetMapping("/{symbol}/price-limit")
    public Map<String, Object> stockPriceLimit(@PathVariable String symbol) {
        return portfolioService.getPriceLimit(symbol);
    }

    @GetMapping("/{symbol}/warnings")
    public Map<String, Object> stockWarnings(@PathVariable String symbol) {
        return portfolioService.getStockWarnings(symbol);
    }
}
