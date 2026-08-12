package com.jihun.portfolio.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * AWS Cost Explorer(ce:GetCostAndUsage)/Billing Credits(billing:GetCredits) 결과를 캐시하는
 * 스케줄러. 두 API 모두 호출당 소액 과금되기도 하지만, 그보다 더 중요한 이유는 AWS 쪽 크레딧·비용
 * 집계 자체가 하루 단위로만 갱신된다는 점이다(당일 안에 여러 번 불러도 새 값이 안 나옴). 그래서
 * 일정 시간마다 다시 부르는 TTL 캐시 대신, 매일 자정(Asia/Seoul) 딱 한 번만 새로 조회해 메모리에
 * 담아두고 대시보드는 이 캐시를 그대로 반환한다. 환율(USD/KRW)도 같은 주기로 함께 갱신한다.
 */
@Service
public class AwsBillingCacheService {

    private static final Logger log = LoggerFactory.getLogger(AwsBillingCacheService.class);
    private static final int COST_WINDOW_DAYS = 7;

    private final AwsBillingService awsBillingService;

    private volatile Map<String, Object> creditUsageCache;
    private volatile Map<String, Object> creditsCache;

    public AwsBillingCacheService(AwsBillingService awsBillingService) {
        this.awsBillingService = awsBillingService;
    }

    /** 서버 시작 15초 후 1회 즉시 채워둔다 — 자정까지 기다리면 그 사이엔 빈 화면이 뜨기 때문. */
    @Scheduled(initialDelay = 15_000, fixedDelay = Long.MAX_VALUE)
    public void initialLoad() {
        refresh();
    }

    /** 이후로는 매일 자정(Asia/Seoul) 1회만 AWS에 재조회한다. */
    @Scheduled(cron = "0 0 0 * * *", zone = "Asia/Seoul")
    public void dailyRefresh() {
        refresh();
    }

    private synchronized void refresh() {
        log.info("[admin-dashboard] AWS 크레딧 소모량/잔액/환율 일일 갱신 시작");
        try {
            awsBillingService.refreshExchangeRate();
            creditUsageCache = awsBillingService.getDailyCreditUsage(COST_WINDOW_DAYS);
            creditsCache = awsBillingService.getCreditsSummary();
            log.info("[admin-dashboard] AWS 크레딧 소모량/잔액/환율 일일 갱신 완료");
        } catch (Exception e) {
            log.warn("[admin-dashboard] AWS 크레딧 소모량/잔액 갱신 실패: {}", e.getMessage());
        }
    }

    /** 관리자가 IAM 결제 접근을 새로 켠 직후처럼, 다음 자정까지 기다리지 않고 지금 바로 다시 조회하고 싶을 때 사용. */
    public void forceRefresh() {
        refresh();
    }

    public Map<String, Object> getCreditUsage() {
        return creditUsageCache != null ? creditUsageCache : awsBillingService.getDailyCreditUsage(COST_WINDOW_DAYS);
    }

    public Map<String, Object> getCredits() {
        return creditsCache != null ? creditsCache : awsBillingService.getCreditsSummary();
    }
}
