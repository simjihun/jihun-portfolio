package com.jihun.portfolio.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.costexplorer.CostExplorerClient;
import software.amazon.awssdk.services.costexplorer.model.DateInterval;
import software.amazon.awssdk.services.costexplorer.model.Granularity;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageRequest;
import software.amazon.awssdk.services.costexplorer.model.GetCostAndUsageResponse;
import software.amazon.awssdk.services.costexplorer.model.ResultByTime;
import software.amazon.awssdk.services.freetier.FreeTierClient;
import software.amazon.awssdk.services.freetier.model.FreeTierUsage;
import software.amazon.awssdk.services.freetier.model.GetFreeTierUsageRequest;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * AWS 프리티어 사용량 + 일별 비용 조회.
 * Free Tier API(freetier:GetFreeTierUsage)는 2023년 공개된 무료 조회 API라 몇 번을 불러도 과금이
 * 없다. 반면 Cost Explorer API(ce:GetCostAndUsage)는 호출 1건당 소액 과금되므로, 이 서비스를 직접
 * 자주 호출하지 말고 반드시 상위(AdminDashboardController 등)에서 캐시를 거쳐 호출 빈도를 줄여야
 * 한다. 두 API 모두 us-east-1 리전에서만 제공돼 리전을 코드에 고정했다(EC2 리전과 무관).
 * 자격증명은 AWS SDK 기본 체인이 환경변수 AWS_ACCESS_KEY_ID/AWS_SECRET_ACCESS_KEY를 자동으로
 * 읽으므로 이 클래스에서 별도로 자격증명을 다루지 않는다(conf/private.conf에만 넣으면 됨).
 */
@Service
public class AwsBillingService {

    private static final Logger log = LoggerFactory.getLogger(AwsBillingService.class);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final int FREE_TIER_TOTAL_DAYS = 365; // 클래식(12개월) 프리티어 기준. 계정 생성일 설정 시에만 사용.

    @Value("${app.aws.account-created-date:}")
    private String accountCreatedDate; // YYYY-MM-DD, 선택 입력(설정해야 잔여일수 계산 가능)

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
        addExpiryInfo(result);
        return result;
    }

    private void addExpiryInfo(Map<String, Object> result) {
        if (accountCreatedDate == null || accountCreatedDate.isBlank()) {
            result.put("expiryNote", "계정 생성일이 설정되지 않아 프리티어 잔여일수를 계산할 수 없습니다 (환경변수 ACCOUNT_CREATED_DATE, 형식 YYYY-MM-DD)");
            return;
        }
        try {
            LocalDate created = LocalDate.parse(accountCreatedDate, DATE_FMT);
            LocalDate expiry = created.plusDays(FREE_TIER_TOTAL_DAYS);
            result.put("freeTierExpiryDate", expiry.toString());
            result.put("daysRemaining", ChronoUnit.DAYS.between(LocalDate.now(), expiry));
        } catch (Exception e) {
            result.put("expiryNote", "ACCOUNT_CREATED_DATE 형식이 올바르지 않습니다 (YYYY-MM-DD)");
        }
    }

    /** 최근 days일 일별 비용(USD). 호출당 과금되니 상위에서 반드시 캐시해서 호출할 것. */
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
                p.put("amount", metric != null ? Double.parseDouble(metric.amount()) : 0.0);
                points.add(p);
            }
            result.put("available", true);
            result.put("points", points);
            result.put("currency", "USD");
        } catch (Exception e) {
            log.warn("[admin-dashboard] 비용 조회 실패: {}", e.getMessage());
            result.put("available", false);
            result.put("message", "비용 데이터를 가져오지 못했습니다 (" + e.getMessage()
                    + ") — Cost Explorer를 청구서 콘솔에서 한 번 활성화했는지 확인하세요(최대 24시간 소요)");
        }
        return result;
    }
}
