package com.jihun.portfolio.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관리자 대시보드용 시스템 정보 API. SecurityConfig의 "/api/admin/**" 규칙으로 ROLE_ADMIN만 접근 가능.
 *
 * AWS Cost Explorer(ce:GetCostAndUsage)는 호출 1건당 소액 과금되는 API라, 페이지를 열 때마다 매번
 * 실시간 호출하지 않고 이 컨트롤러에서 6시간 캐시한다 — 하루에 최대 4번만 실제로 AWS에 요청이
 * 나가므로 비용을 사실상 무시할 수 있는 수준으로 유지한다. Free Tier 사용량 API는 무료라 캐시 없이
 * 매번 최신값을 조회한다.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private static final long COST_CACHE_TTL_MILLIS = 6 * 3600_000L;

    private final AwsInstanceInfoService awsInstanceInfoService;
    private final DatabaseInfoService databaseInfoService;
    private final AwsBillingService awsBillingService;

    private record CacheEntry(long expiresAt, Map<String, Object> value) {}
    private final Map<String, CacheEntry> costCache = new ConcurrentHashMap<>();

    public AdminDashboardController(AwsInstanceInfoService awsInstanceInfoService,
                                     DatabaseInfoService databaseInfoService,
                                     AwsBillingService awsBillingService) {
        this.awsInstanceInfoService = awsInstanceInfoService;
        this.databaseInfoService = databaseInfoService;
        this.awsBillingService = awsBillingService;
    }

    @GetMapping("/aws")
    public Map<String, Object> aws() {
        return awsInstanceInfoService.getInfo();
    }

    @GetMapping("/database")
    public Map<String, Object> database() {
        return databaseInfoService.getInfo();
    }

    /** 무료 API라 캐시 없이 매번 최신값 조회 */
    @GetMapping("/free-tier")
    public Map<String, Object> freeTier() {
        return awsBillingService.getFreeTierUsage();
    }

    /** 유료 API(호출당 과금)라 6시간 캐시 후에만 재호출 */
    @GetMapping("/cost")
    public synchronized Map<String, Object> cost() {
        String key = "daily-cost-14d";
        CacheEntry cached = costCache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now < cached.expiresAt()) return cached.value();
        Map<String, Object> value = awsBillingService.getDailyCost(14);
        costCache.put(key, new CacheEntry(now + COST_CACHE_TTL_MILLIS, value));
        return value;
    }
}
