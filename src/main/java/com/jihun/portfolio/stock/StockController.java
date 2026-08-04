package com.jihun.portfolio.stock;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 주식 대시보드 REST API. 모든 응답은 서버 측 TTL 캐시를 거친다(외부 API·프리티어 보호).
 */
@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final StockDashboardService service;

    public StockController(StockDashboardService service) {
        this.service = service;
    }

    /** 상단 지표 + 수급 + 장 캘린더를 한 번에 반환 (페이지 초기 로딩용) */
    @GetMapping("/dashboard")
    public Map<String, Object> dashboard() {
        Map<String, Object> res = new HashMap<>();
        res.put("indicators", service.getIndicators());
        res.put("investorTrading", service.getInvestorTrading());
        res.put("calendar", service.getCalendar());
        return res;
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
}
