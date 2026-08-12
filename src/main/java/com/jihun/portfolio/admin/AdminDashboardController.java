package com.jihun.portfolio.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 관리자 대시보드용 시스템 정보 API. SecurityConfig의 "/api/admin/**" 규칙으로 ROLE_ADMIN만 접근 가능.
 *
 * cost/credits는 AwsBillingCacheService가 매일 자정 1회만 갱신해둔 캐시를 그대로 반환한다
 * (AWS 집계 자체가 일단위 갱신이라 더 자주 조회해도 새 값이 안 나옴 + 호출당 과금 방지).
 * aws/database/free-tier는 무료이거나 이미 있는 자격증명만 쓰는 조회라 매번 최신값을 가져온다.
 */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AwsInstanceInfoService awsInstanceInfoService;
    private final DatabaseInfoService databaseInfoService;
    private final AwsBillingService awsBillingService;
    private final AwsBillingCacheService awsBillingCacheService;

    public AdminDashboardController(AwsInstanceInfoService awsInstanceInfoService,
                                     DatabaseInfoService databaseInfoService,
                                     AwsBillingService awsBillingService,
                                     AwsBillingCacheService awsBillingCacheService) {
        this.awsInstanceInfoService = awsInstanceInfoService;
        this.databaseInfoService = databaseInfoService;
        this.awsBillingService = awsBillingService;
        this.awsBillingCacheService = awsBillingCacheService;
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

    /** 매일 자정 갱신된 캐시 반환 */
    @GetMapping("/cost")
    public Map<String, Object> cost() {
        return awsBillingCacheService.getCost();
    }

    /** 매일 자정 갱신된 캐시 반환 — 남은 크레딧·소진 예상일 */
    @GetMapping("/credits")
    public Map<String, Object> credits() {
        return awsBillingCacheService.getCredits();
    }
}
