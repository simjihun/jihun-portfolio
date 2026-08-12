package com.jihun.portfolio.portfolio;

import org.springframework.web.bind.annotation.GetMapping;
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
}
