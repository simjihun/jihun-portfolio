package com.jihun.portfolio.portfolio;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * TOSS 주식(개인 보유 종목) API. SecurityConfig의 "/api/admin/**" 규칙으로 ROLE_ADMIN만 접근 가능.
 * 조회 전용 — 매수/매도 엔드포인트는 없다(실제 매매는 토스증권 앱에서 직접 진행).
 */
@RestController
@RequestMapping("/api/admin/portfolio")
public class TossPortfolioController {

    private final TossPortfolioService portfolioService;

    public TossPortfolioController(TossPortfolioService portfolioService) {
        this.portfolioService = portfolioService;
    }

    @GetMapping("/holdings")
    public Map<String, Object> holdings() {
        return portfolioService.getHoldings();
    }

    /** status: OPEN(진행중) | CLOSED(체결완료·취소 등 종료) */
    @GetMapping("/orders")
    public Map<String, Object> orders(@RequestParam(defaultValue = "CLOSED") String status,
                                       @RequestParam(required = false) String symbol) {
        return portfolioService.getOrders(status, symbol);
    }

    /** interval: 1m 또는 1d(토스 API가 지원하는 값은 이 둘뿐 — 주/월/년봉과 N분봉은 프론트에서 1m/1d 데이터를 집계) */
    @GetMapping("/stock/{symbol}/chart")
    public Map<String, Object> chart(@PathVariable String symbol,
                                      @RequestParam(defaultValue = "1d") String interval,
                                      @RequestParam(defaultValue = "200") int count) {
        return portfolioService.getChart(symbol, interval, count);
    }

    @GetMapping("/stock/{symbol}/orderbook")
    public Map<String, Object> orderbook(@PathVariable String symbol) {
        return portfolioService.getOrderbook(symbol);
    }

    /** count: 최근 며칠치를 볼지(최신순). 프론트는 7일치만 표시한다. */
    @GetMapping("/stock/{symbol}/investor-trading")
    public Map<String, Object> investorTrading(@PathVariable String symbol,
                                                @RequestParam(defaultValue = "7") int count) {
        return portfolioService.getInvestorTrading(symbol, count);
    }

    @GetMapping("/stock/{symbol}/short-selling")
    public Map<String, Object> shortSelling(@PathVariable String symbol,
                                             @RequestParam(defaultValue = "7") int count) {
        return portfolioService.getShortSelling(symbol, count);
    }
}
