package com.jihun.portfolio.portfolio;

import com.jihun.portfolio.stock.TossApiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 토스증권 Open API로 본인 계좌의 보유 종목·주문 내역을 조회하는 서비스. 조회 전용이다 —
 * 매수/매도(주문 생성) 엔드포인트는 의도적으로 구현하지 않는다(실제 매매는 토스증권 앱에서 직접).
 *
 * 계좌 목록(GET /api/v1/accounts)의 accountSeq를 모든 이후 호출(X-Tossinvest-Account 헤더)에
 * 사용한다. 계좌 유형은 현재 BROKERAGE(종합매매, 국내·해외 주식 통합)만 지원되므로 보통 계좌가
 * 하나뿐이라 첫 번째 계좌를 기본으로 쓴다.
 *
 * accountSeq는 자주 안 바뀌는 값이라 짧게 캐시해 /accounts 호출 빈도를 줄인다.
 *
 * 차트·호가·수급(투자자별 매매동향)·공매도는 계좌와 무관한 공개 시세 데이터라 X-Tossinvest-Account
 * 헤더 없이 조회한다(get(path)만 사용, accountSeq 불필요). 응답 스키마가 중첩이 깊어 굳이 자바
 * 타입으로 다 옮겨적지 않고, "result"만 벗겨서 프론트에 그대로 넘긴다 — 프론트에서 필요한 필드만
 * 안전하게 꺼내 쓰도록 한다(중첩 필드명을 서버에서 하나하나 틀리게 옮겨 적어 배포가 깨지는 위험을 줄임).
 */
@Service
public class TossPortfolioService {

    private static final Logger log = LoggerFactory.getLogger(TossPortfolioService.class);

    private final TossApiClient toss;

    private volatile Long cachedAccountSeq;
    private volatile String cachedAccountNo;

    public TossPortfolioService(TossApiClient toss) {
        this.toss = toss;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> res) {
        if (res == null) return null;
        Object r = res.get("result");
        return r instanceof Map ? (Map<String, Object>) r : res;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> listOf(Map<String, Object> raw, String... keys) {
        if (raw == null) return List.of();
        Object r = raw.get("result");
        if (r instanceof List) return (List<Map<String, Object>>) r;
        Map<String, Object> base = r instanceof Map ? (Map<String, Object>) r : raw;
        for (String k : keys) {
            Object v = base.get(k);
            if (v instanceof List) return (List<Map<String, Object>>) v;
        }
        return List.of();
    }

    private Double num(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try { return Double.parseDouble(v.toString()); } catch (Exception e) { return null; }
    }

    /** 첫 번째 계좌의 accountSeq를 반환(짧게 캐시). 계좌가 없거나 조회 실패 시 null. */
    private synchronized Long resolveAccountSeq() {
        if (cachedAccountSeq != null) return cachedAccountSeq;
        List<Map<String, Object>> accounts = listOf(toss.get("/api/v1/accounts"), "accounts", "items", "list");
        if (accounts.isEmpty()) return null;
        Map<String, Object> first = accounts.get(0);
        Object seq = first.get("accountSeq");
        cachedAccountSeq = seq instanceof Number n ? n.longValue() : null;
        cachedAccountNo = (String) first.get("accountNo");
        return cachedAccountSeq;
    }

    /**
     * 보유 종목 + 계좌 전체 요약(투자원금/평가금액/손익). GET /api/v1/holdings.
     * 손익률(rate)은 소수비율로 내려오므로 ×100 해서 % 단위로 정규화해 반환한다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getHoldings() {
        Map<String, Object> result = new LinkedHashMap<>();
        Long accountSeq = resolveAccountSeq();
        if (accountSeq == null) {
            result.put("available", false);
            result.put("message", "토스증권 계좌를 찾지 못했습니다. 계좌 API 개통 여부와 계정 설정의 토스 API 키를 확인하세요.");
            return result;
        }
        Map<String, Object> raw = unwrap(toss.get("/api/v1/holdings", accountSeq));
        if (raw == null) {
            result.put("available", false);
            result.put("message", "보유 종목을 가져오지 못했습니다.");
            return result;
        }

        Map<String, Object> totalProfitLoss = (Map<String, Object>) raw.get("profitLoss");
        Map<String, Object> totalMarketValue = (Map<String, Object>) raw.get("marketValue");
        Map<String, Object> totalPurchase = (Map<String, Object>) raw.get("totalPurchaseAmount");

        List<Map<String, Object>> items = new ArrayList<>();
        for (Object o : (List<Object>) raw.getOrDefault("items", List.of())) {
            Map<String, Object> it = (Map<String, Object>) o;
            Map<String, Object> pl = (Map<String, Object>) it.get("profitLoss");
            Map<String, Object> mv = (Map<String, Object>) it.get("marketValue");
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("symbol", it.get("symbol"));
            n.put("name", it.get("name"));
            n.put("marketCountry", it.get("marketCountry"));
            n.put("currency", it.get("currency"));
            n.put("quantity", num(it.get("quantity")));
            n.put("lastPrice", num(it.get("lastPrice")));
            n.put("averagePurchasePrice", num(it.get("averagePurchasePrice")));
            n.put("marketValue", mv != null ? num(mv.get("amount")) : null);
            n.put("profitLossAmount", pl != null ? num(pl.get("amount")) : null);
            Double rate = pl != null ? num(pl.get("rate")) : null;
            n.put("profitLossRate", rate != null ? Math.round(rate * 10000.0) / 100.0 : null);
            items.add(n);
        }
        // 평가금액 큰 순으로 정렬(원화·달러 섞여있어도 개인 화면이라 대략적인 순서로 충분)
        items.sort((a, b) -> {
            double va = a.get("marketValue") == null ? 0 : (double) a.get("marketValue");
            double vb = b.get("marketValue") == null ? 0 : (double) b.get("marketValue");
            return Double.compare(vb, va);
        });

        result.put("available", true);
        result.put("accountNo", cachedAccountNo);
        result.put("totalPurchaseAmount", totalPurchase != null ? num(totalPurchase.get("amount")) : null);
        result.put("totalMarketValue", totalMarketValue != null ? num(totalMarketValue.get("amount")) : null);
        result.put("totalProfitLossAmount", totalProfitLoss != null ? num(totalProfitLoss.get("amount")) : null);
        Double totalRate = totalProfitLoss != null ? num(totalProfitLoss.get("rate")) : null;
        result.put("totalProfitLossRate", totalRate != null ? Math.round(totalRate * 10000.0) / 100.0 : null);
        result.put("items", items);
        return result;
    }

    /**
     * 주문 내역. GET /api/v1/orders. status=OPEN(진행중)|CLOSED(종료, 체결완료·취소 등).
     * symbol을 지정하면 그 종목만. 개별 주문의 실제 상태(FILLED/CANCELED/REJECTED 등)는 status
     * 필드에 그대로 담아 반환하므로, "체결된 것만 보기" 같은 세부 필터는 프론트에서 이 필드로 거른다.
     * 매수/매도 자체는 하지 않고 이미 낸 주문을 조회만 한다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrders(String status, String symbol) {
        Map<String, Object> result = new LinkedHashMap<>();
        Long accountSeq = resolveAccountSeq();
        if (accountSeq == null) {
            result.put("available", false);
            result.put("message", "토스증권 계좌를 찾지 못했습니다.");
            return result;
        }
        String s = "OPEN".equalsIgnoreCase(status) ? "OPEN" : "CLOSED";
        StringBuilder path = new StringBuilder("/api/v1/orders?status=").append(s).append("&limit=100");
        if (symbol != null && !symbol.isBlank()) path.append("&symbol=").append(symbol.trim());

        Map<String, Object> raw = unwrap(toss.get(path.toString(), accountSeq));
        List<Map<String, Object>> orders = new ArrayList<>();
        if (raw != null) {
            for (Object o : (List<Object>) raw.getOrDefault("orders", List.of())) {
                Map<String, Object> it = (Map<String, Object>) o;
                Map<String, Object> execution = (Map<String, Object>) it.get("execution");
                Map<String, Object> n = new LinkedHashMap<>();
                n.put("orderId", it.get("orderId"));
                n.put("symbol", it.get("symbol"));
                n.put("side", it.get("side"));
                n.put("orderType", it.get("orderType"));
                n.put("status", it.get("status"));
                n.put("price", num(it.get("price")));
                n.put("quantity", num(it.get("quantity")));
                n.put("currency", it.get("currency"));
                n.put("orderedAt", it.get("orderedAt"));
                n.put("filledQuantity", execution != null ? num(execution.get("filledQuantity")) : 0);
                n.put("filledAveragePrice", execution != null ? num(execution.get("averagePrice")) : null);
                orders.add(n);
            }
        }
        result.put("available", raw != null);
        if (raw == null) result.put("message", "주문 내역을 가져오지 못했습니다.");
        result.put("orders", orders);
        return result;
    }

    /** 일봉 차트. GET /api/v1/candles. 최근 count개(최대 200). */
    public Map<String, Object> getChart(String symbol, int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> raw = unwrap(toss.get("/api/v1/candles?symbol=" + symbol + "&interval=1d&count=" + Math.min(count, 200)));
        if (raw == null) {
            result.put("available", false);
            result.put("message", "차트 데이터를 가져오지 못했습니다.");
            return result;
        }
        result.put("available", true);
        result.put("candles", raw.getOrDefault("candles", List.of()));
        return result;
    }

    /** 호가. GET /api/v1/orderbook. asks(매도)/bids(매수) 그대로 전달. */
    public Map<String, Object> getOrderbook(String symbol) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> raw = unwrap(toss.get("/api/v1/orderbook?symbol=" + symbol));
        if (raw == null) {
            result.put("available", false);
            result.put("message", "호가 데이터를 가져오지 못했습니다.");
            return result;
        }
        result.put("available", true);
        result.put("asks", raw.getOrDefault("asks", List.of()));
        result.put("bids", raw.getOrDefault("bids", List.of()));
        return result;
    }

    /** 투자자별 매매동향(개인/외국인/기관). GET /api/v1/stocks/{symbol}/investor-trading. */
    public Map<String, Object> getInvestorTrading(String symbol) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> raw = unwrap(toss.get("/api/v1/stocks/" + symbol + "/investor-trading"));
        if (raw == null) {
            result.put("available", false);
            result.put("message", "투자자별 매매동향을 가져오지 못했습니다.");
            return result;
        }
        result.put("available", true);
        result.put("records", raw.getOrDefault("records", raw.getOrDefault("items", List.of())));
        return result;
    }

    /** 공매도 동향. GET /api/v1/stocks/{symbol}/short-selling. */
    public Map<String, Object> getShortSelling(String symbol) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> raw = unwrap(toss.get("/api/v1/stocks/" + symbol + "/short-selling"));
        if (raw == null) {
            result.put("available", false);
            result.put("message", "공매도 동향을 가져오지 못했습니다.");
            return result;
        }
        result.put("available", true);
        result.put("records", raw.getOrDefault("records", raw.getOrDefault("items", List.of())));
        return result;
    }
}
