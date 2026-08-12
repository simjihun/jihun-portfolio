package com.jihun.portfolio.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.billing.BillingClient;
import software.amazon.awssdk.services.billing.model.CreditData;
import software.amazon.awssdk.services.billing.model.GetCreditsRequest;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;
import software.amazon.awssdk.services.freetier.FreeTierClient;
import software.amazon.awssdk.services.freetier.model.FreeTierUsage;
import software.amazon.awssdk.services.freetier.model.GetFreeTierUsageRequest;
import software.amazon.awssdk.services.sts.StsClient;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AWS 프리티어 사용량 + 일별 비용 + 계정 크레딧 잔액 조회.
 *
 * - Free Tier API(freetier:GetFreeTierUsage)와 STS(sts:GetCallerIdentity)는 무료라 매번 호출해도 된다.
 * - Cost Explorer(ce:GetCostAndUsage)와 Billing Credits(billing:GetCredits)는 호출당 소액 과금되는
 *   API라, 이 서비스를 직접 자주 부르지 말고 반드시 상위(AdminDashboardController)에서 캐시를 거쳐
 *   호출 빈도를 낮춰야 한다.
 * - 세 API 모두 us-east-1 리전에서만 제공돼 리전을 코드에 고정했다(EC2 리전과 무관).
 * - 자격증명은 AWS SDK 기본 체인이 환경변수 AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY를 자동으로
 *   읽으므로 이 클래스에서 별도로 자격증명을 다루지 않는다(conf/private.conf에만 넣으면 됨).
 */
@Service
public class AwsBillingService {

    private static final Logger log = LoggerFactory.getLogger(AwsBillingService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    // 대시보드 표시용 근사 환율(고정값). 정확한 회계용이 아니라 "대략 얼마 쓰고 있는지" 감을 잡기 위한
    // 용도라 실시간 환율 API를 별도로 붙이지 않았다. 필요하면 이 상수만 주기적으로 갱신하면 된다.
    private static final double USD_TO_KRW = 1380.0;

    private volatile String cachedAccountId; // 세션 내내 안 바뀌는 값이라 한 번 조회 후 재사용

    public Map<String, Object> getFreeTierUsage() {
        Map<String, Object> result = new LinkedHashMap<>();
        try (FreeTierClient client = FreeTierClient.builder().region(Region.US_EAST_1).build()) {
            List<Map<String, Object>> items = new ArrayList<>();
            String nextToken = null;
            do {
                GetFreeTierUsageRequest.Builder reqBuilder = GetFreeTierUsageRequest.builder().maxResults(100);
                if (nextToken != null) reqBuilder.nextToken(nextToken);
                var res = client.getFreeTierUsage(reqBuilder.build());
                for (FreeTierUsage u : res.freeTierUsages()) {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("service", u.service());
                    m.put("usageType", u.usageType());
                    m.put("unit", u.unit());
                    m.put("actualUsage", u.actualUsageAmount());
                    m.put("forecastedUsage", u.forecastedUsageAmount());
                    m.put("limit", u.limit());
                    Double limit = u.limit(), actual = u.actualUsageAmount();
                    m.put("percent", (limit != null && limit > 0 && actual != null)
                            ? Math.round(actual / limit * 1000.0) / 10.0 : null);
                    items.add(m);
                }
                nextToken = res.nextToken();
            } while (nextToken != null);

            items.sort((a, b) -> {
                double pa = a.get("percent") == null ? -1 : (double) a.get("percent");
                double pb = b.get("percent") == null ? -1 : (double) b.get("percent");
                return Double.compare(pb, pa);
            });
            result.put("available", true);
            result.put("items", items);
        } catch (Exception e) {
            log.warn("[admin-dashboard] Free Tier 사용량 조회 실패: {}", e.getMessage());
            result.put("available", false);
            result.put("message", "Free Tier 사용량을 가져오지 못했습니다 (" + e.getMessage()
                    + ") — AWS_ACCESS_KEY_ID/SECRET이 conf/private.conf에 설정되어 있는지 확인하세요");
        }
        return result;
    }

    /**
     * 계정 크레딧 잔액 + 소진 예상일. AWS 콘솔 "남은 크레딧 / 남은 기간" 위젯과 같은 값을 보여준다.
     * exhaustDate(현재 소비 속도로 계산한 잔액 0 도달 예상일)를 우선 쓰고, 없으면 endDate(크레딧
     * 하드 만료일)로 대체한다.
     */
    public Map<String, Object> getCreditsSummary() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String accountId = resolveAccountId();
            long now = Instant.now().getEpochSecond();
            long yearAgo = Instant.now().minus(364, ChronoUnit.DAYS).getEpochSecond();

            try (BillingClient client = BillingClient.builder().region(Region.US_EAST_1).build()) {
                GetCreditsRequest req = GetCreditsRequest.builder()
                        .accountId(accountId)
                        .startDate(yearAgo)
                        .endDate(now)
                        .build();
                var res = client.getCredits(req);

                double totalRemaining = 0;
                String currency = "USD";
                Long earliestExhaust = null;
                Long earliestEnd = null;
                List<Map<String, Object>> credits = new ArrayList<>();

                for (CreditData c : res.credits()) {
                    if (c.creditStatusAsString() != null && !"ACTIVE".equalsIgnoreCase(c.creditStatusAsString())) continue;
                    if (c.estimatedAmount() != null) {
                        totalRemaining += Double.parseDouble(c.estimatedAmount().currencyAmount());
                        currency = c.estimatedAmount().currencyCode();
                    }
                    if (c.exhaustDate() != null && (earliestExhaust == null || c.exhaustDate() < earliestExhaust)) {
                        earliestExhaust = c.exhaustDate();
                    }
                    if (c.endDate() != null && (earliestEnd == null || c.endDate() < earliestEnd)) {
                        earliestEnd = c.endDate();
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("description", c.description());
                    m.put("remaining", c.estimatedAmount() != null ? c.estimatedAmount().currencyAmount() : null);
                    m.put("endDate", c.endDate() != null ? epochToDate(c.endDate()) : null);
                    m.put("exhaustDate", c.exhaustDate() != null ? epochToDate(c.exhaustDate()) : null);
                    credits.add(m);
                }

                Long targetEpoch = earliestExhaust != null ? earliestExhaust : earliestEnd;
                result.put("available", true);
                result.put("totalRemaining", Math.round(totalRemaining * 100.0) / 100.0);
                result.put("totalRemainingKrw", Math.round(totalRemaining * USD_TO_KRW));
                result.put("currency", currency);
                result.put("dateBasis", earliestExhaust != null ? "exhaust" : "expiry"); // 소진예상 vs 하드만료
                if (targetEpoch != null) {
                    LocalDate targetDate = epochToDate(targetEpoch);
                    result.put("targetDate", targetDate.toString());
                    result.put("daysRemaining", ChronoUnit.DAYS.between(LocalDate.now(), targetDate));
                }
                result.put("credits", credits);
            }
        } catch (Exception e) {
            log.warn("[admin-dashboard] 크레딧 조회 실패: {}", e.getMessage());
            result.put("available", false);
            result.put("message", "크레딧 정보를 가져오지 못했습니다 (" + e.getMessage()
                    + ") — IAM 정책에 billing:GetCredits, sts:GetCallerIdentity가 있는지 확인하세요");
        }
        return result;
    }

    private String resolveAccountId() {
        if (cachedAccountId != null) return cachedAccountId;
        try (StsClient sts = StsClient.builder().region(Region.US_EAST_1).build()) {
            cachedAccountId = sts.getCallerIdentity().account();
            return cachedAccountId;
        }
    }

    private LocalDate epochToDate(long epochSeconds) {
        return Instant.ofEpochSecond(epochSeconds).atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * 최근 days일 일별 비용. USD 원본과 원화 환산(고정 환율)을 함께 반환한다.
     * 음수(부동소수점 반올림으로 생기는 -0.0000001 같은 잡음)는 0으로 클램프한다 — 이 지표에서
     * 실제로 비용이 마이너스일 일은 없고, 그래프에서 기준선 아래로 삐져나와 보기만 불편해진다.
     * 호출당 과금되니 상위에서 반드시 캐시해서 호출할 것.
     */
    public Map<String, Object> getDailyCost(int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (CostExplorerClient client = CostExplorerClient.builder().region(Region.US_EAST_1).build()) {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(days);
            GetCostAndUsageRequest req = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder().start(start.format(DATE_FMT)).end(end.format(DATE_FMT)).build())
                    .granularity(Granularity.DAILY)
                    .metrics("UnblendedCost")
                    .build();
            GetCostAndUsageResponse res = client.getCostAndUsage(req);

            List<Map<String, Object>> points = new ArrayList<>();
            for (ResultByTime r : res.resultsByTime()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("date", r.timePeriod().start());
                var metric = r.total().get("UnblendedCost");
                double usd = metric != null ? Double.parseDouble(metric.amount()) : 0.0;
                usd = Math.max(0, usd);
                p.put("amountUsd", usd);
                p.put("amountKrw", Math.round(usd * USD_TO_KRW * 100.0) / 100.0);
                points.add(p);
            }
            result.put("available", true);
            result.put("points", points);
            result.put("exchangeRate", USD_TO_KRW);
        } catch (Exception e) {
            log.warn("[admin-dashboard] 비용 조회 실패: {}", e.getMessage());
            result.put("available", false);
            result.put("message", "비용 데이터를 가져오지 못했습니다 (" + e.getMessage()
                    + ") — Cost Explorer를 청구서 콘솔에서 한 번 활성화했는지 확인하세요(최대 24시간 소요)");
        }
        return result;
    }
}
