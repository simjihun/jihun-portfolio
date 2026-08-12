package com.jihun.portfolio.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.billing.BillingClient;
import software.amazon.awssdk.services.billing.model.CreditData;
import software.amazon.awssdk.services.billing.model.GetCreditsRequest;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Dimension;
import software.amazon.awssdk.services.costexplorer.model.DimensionValues;
import software.amazon.awssdk.services.costexplorer.model.Expression;
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
 * AWS 프리티어 사용량 + 일별 크레딧 소모량 + 계정 크레딧 잔액 조회.
 *
 * - Free Tier API(freetier:GetFreeTierUsage)와 STS(sts:GetCallerIdentity)는 무료라 매번 호출해도 된다.
 * - Cost Explorer(ce:GetCostAndUsage)와 Billing Credits(billing:GetCredits)는 호출당 소액 과금되는
 *   API라, 이 서비스를 직접 자주 부르지 말고 반드시 상위(AwsBillingCacheService)가 하루 1회만 캐시해
 *   호출 빈도를 낮춘다.
 * - 세 API 모두 us-east-1 리전에서만 제공돼 리전을 코드에 고정했다(EC2 리전과 무관).
 * - 자격증명은 AWS SDK 기본 체인이 환경변수 AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY를 자동으로
 *   읽으므로 이 클래스에서 별도로 자격증명을 다루지 않는다(conf/private.conf에만 넣으면 됨).
 * - billing:GetCredits의 startDate/endDate, CreditData의 endDate/exhaustDate는 API 문서상
 *   "Unix epoch seconds"라고 적혀있지만 실제 자바 SDK 모델은 java.time.Instant로 나온다.
 */
@Service
public class AwsBillingService {

    private static final Logger log = LoggerFactory.getLogger(AwsBillingService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    // 하루 1회만 조회하니 무료 공개 API(키 불필요)로 실시간 환율을 받아온다. 실패하면 직전 값(최초엔
    // 이 폴백 상수)을 그대로 쓴다 — 대시보드가 환율 API 장애로 깨지면 안 되기 때문.
    private volatile double usdToKrw = 1380.0;
    private final RestTemplate rest;

    private volatile String cachedAccountId; // 세션 내내 안 바뀌는 값이라 한 번 조회 후 재사용

    public AwsBillingService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5000);
        f.setReadTimeout(5000);
        this.rest = new RestTemplate(f);
    }

    /** 하루 1회(AwsBillingCacheService의 자정 갱신 시점) 실제 환율로 갱신한다. */
    @SuppressWarnings("unchecked")
    public void refreshExchangeRate() {
        try {
            Map<String, Object> body = rest.getForObject("https://open.er-api.com/v6/latest/USD", Map.class);
            Map<String, Object> rates = (Map<String, Object>) body.get("rates");
            Object krw = rates != null ? rates.get("KRW") : null;
            if (krw != null) {
                usdToKrw = Double.parseDouble(krw.toString());
                log.info("[admin-dashboard] USD/KRW 환율 갱신: {}", usdToKrw);
            }
        } catch (Exception e) {
            log.warn("[admin-dashboard] 환율 조회 실패, 이전 값 유지({}): {}", usdToKrw, e.getMessage());
        }
    }

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
            Instant now = Instant.now();
            Instant yearAgo = now.minus(364, ChronoUnit.DAYS);

            try (BillingClient client = BillingClient.builder().region(Region.US_EAST_1).build()) {
                GetCreditsRequest req = GetCreditsRequest.builder()
                        .accountId(accountId)
                        .startDate(yearAgo)
                        .endDate(now)
                        .build();
                var res = client.getCredits(req);

                double totalRemaining = 0;
                String currency = "USD";
                Instant earliestExhaust = null;
                Instant earliestEnd = null;
                List<Map<String, Object>> credits = new ArrayList<>();

                for (CreditData c : res.credits()) {
                    if (c.creditStatusAsString() != null && !"ACTIVE".equalsIgnoreCase(c.creditStatusAsString())) continue;
                    if (c.estimatedAmount() != null) {
                        totalRemaining += Double.parseDouble(c.estimatedAmount().currencyAmount());
                        currency = c.estimatedAmount().currencyCode();
                    }
                    if (c.exhaustDate() != null && (earliestExhaust == null || c.exhaustDate().isBefore(earliestExhaust))) {
                        earliestExhaust = c.exhaustDate();
                    }
                    if (c.endDate() != null && (earliestEnd == null || c.endDate().isBefore(earliestEnd))) {
                        earliestEnd = c.endDate();
                    }
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("description", c.description());
                    m.put("remaining", c.estimatedAmount() != null ? c.estimatedAmount().currencyAmount() : null);
                    m.put("endDate", c.endDate() != null ? toLocalDate(c.endDate()) : null);
                    m.put("exhaustDate", c.exhaustDate() != null ? toLocalDate(c.exhaustDate()) : null);
                    credits.add(m);
                }

                Instant target = earliestExhaust != null ? earliestExhaust : earliestEnd;
                result.put("available", true);
                result.put("totalRemaining", Math.round(totalRemaining * 100.0) / 100.0);
                result.put("totalRemainingKrw", Math.round(totalRemaining * usdToKrw));
                result.put("currency", currency);
                result.put("dateBasis", earliestExhaust != null ? "exhaust" : "expiry"); // 소진예상 vs 하드만료
                if (target != null) {
                    LocalDate targetDate = toLocalDate(target);
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

    private LocalDate toLocalDate(Instant instant) {
        return instant.atZone(ZoneOffset.UTC).toLocalDate();
    }

    /**
     * 최근 days일 일별 "크레딧 소모량". 일반 UnblendedCost(RECORD_TYPE 구분 없음)는 크레딧으로
     * 상쇄된 뒤의 실질 비용이 아니라 발생한 사용 비용 그 자체라, 프리티어 한도 안에서만 쓰고 있으면
     * 계속 $0으로 나와 무료 크레딧이 실제로 얼마나 빠지고 있는지 보여주지 못한다. 그래서
     * RECORD_TYPE=Credit으로 필터링해서, 그날 청구서에 "크레딧"으로 잡힌 항목만 따로 뽑는다
     * (크레딧 라인아이템은 원래 음수로 잡히므로 절댓값을 사용).
     * 호출당 과금되니 상위(AwsBillingCacheService)에서 반드시 캐시해서 호출할 것.
     */
    public Map<String, Object> getDailyCreditUsage(int days) {
        Map<String, Object> result = new LinkedHashMap<>();
        try (CostExplorerClient client = CostExplorerClient.builder().region(Region.US_EAST_1).build()) {
            LocalDate end = LocalDate.now();
            LocalDate start = end.minusDays(days);
            Expression creditFilter = Expression.builder()
                    .dimensions(DimensionValues.builder()
                            .key(Dimension.RECORD_TYPE)
                            .values("Credit")
                            .build())
                    .build();
            GetCostAndUsageRequest req = GetCostAndUsageRequest.builder()
                    .timePeriod(DateInterval.builder().start(start.format(DATE_FMT)).end(end.format(DATE_FMT)).build())
                    .granularity(Granularity.DAILY)
                    .metrics("UnblendedCost")
                    .filter(creditFilter)
                    .build();
            GetCostAndUsageResponse res = client.getCostAndUsage(req);

            List<Map<String, Object>> points = new ArrayList<>();
            for (ResultByTime r : res.resultsByTime()) {
                Map<String, Object> p = new LinkedHashMap<>();
                p.put("date", r.timePeriod().start());
                var metric = r.total().get("UnblendedCost");
                double usd = metric != null ? Math.abs(Double.parseDouble(metric.amount())) : 0.0;
                p.put("amountUsd", usd);
                p.put("amountKrw", Math.round(usd * usdToKrw * 100.0) / 100.0);
                points.add(p);
            }
            result.put("available", true);
            result.put("points", points);
            result.put("exchangeRate", usdToKrw);
        } catch (Exception e) {
            log.warn("[admin-dashboard] 크레딧 소모량 조회 실패: {}", e.getMessage());
            result.put("available", false);
            result.put("message", "크레딧 소모량 데이터를 가져오지 못했습니다 (" + e.getMessage()
                    + ") — Cost Explorer를 청구서 콘솔에서 한 번 활성화했는지 확인하세요(최대 24시간 소요)");
        }
        return result;
    }
}
