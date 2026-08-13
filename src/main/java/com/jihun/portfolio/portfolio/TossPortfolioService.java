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
 * 헤더 없이 조회한다(get(path)만 사용, accountSeq 불필요).
 *
 * [주의] 토스 API는 BigDecimal 필드(가격·거래량 등)를 JSON에서 "16850.0000" 같은 숫자 문자열로
 * 내려준다(부동소수점 오차 방지 목적). 이걸 그대로 프론트에 넘기면 JS에서 문자열로 취급되어
 * 산술 연산(특히 `+`)이 숫자 덧셈이 아니라 문자열 접합으로 동작해버린다 — 예: 이동평균 계산에서
 * `sum += "16850.0000"`을 반복하면 숫자가 아니라 자릿수가 계속 늘어나는 문자열이 만들어지고,
 * 이걸 나중에 나눗셈에서 다시 숫자로 바꾸면 1e+98 같은 말도 안 되는 값이 나온다. 그래서 여기서는
 * 응답을 raw 그대로 넘기지 않고, 모든 수치 필드를 서버에서 Double로 확정해서 내려준다.
 *
 * [주의] 종목별(HoldingsItem) marketValue/profitLoss는 그 종목 통화 단일 금액(amount: BigDecimal)이지만,
 * 계좌 합계(HoldingsOverview)의 totalPurchaseAmount/marketValue/profitLoss는 원화·달러가 섞여있어서
 * amount가 아니라 {krw, usd} 두 값으로 따로 내려온다(Price 타입) — 처음에 이 차이를 놓쳐서 합계가 계속
 * "-"로 보이던 버그가 있었음. 여기서는 실시간 환율(GET /api/v1/exchange-rate)로 달러를 원화 환산해
 * 하나의 원화 합계로 합쳐서 반환한다.
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

    /** 토스 API가 BigDecimal을 숫자 문자열("16850.0000")로 내려주는 경우까지 안전하게 Double로 변환한다. */
    private Double num(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        String s = v.toString().trim();
        if (s.isEmpty()) return null;
        try { return Double.parseDouble(s); } catch (Exception e) { return null; }
    }

    /** 후보 키를 순서대로 시도해 첫 번째로 파싱되는 숫자를 반환한다(필드명이 문서상 불확실할 때 방어적으로 사용). */
    private Double numAny(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Double v = num(m.get(k));
            if (v != null) return v;
        }
        return null;
    }

    private String str(Object v) {
        return v == null ? null : v.toString();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOf(Object v) {
        return v instanceof Map ? (Map<String, Object>) v : null;
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

    /** USD→KRW 환율(매수 환율). 실패 시 null — 그 경우 달러 보유분은 합계에서 제외된다(0 취급 대신 아예 계산 생략은 과함이라, 최소한 원화분만이라도 보여줌). */
    private Double fetchUsdToKrw() {
        Map<String, Object> raw = unwrap(toss.get("/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW"));
        return raw != null ? num(raw.get("rate")) : null;
    }

    /** Price 타입({krw, usd}) 하나를 환율로 원화 환산해 합산한다. */
    private double toKrwTotal(Map<String, Object> priceObj, Double usdToKrw) {
        if (priceObj == null) return 0;
        double krw = Optional.ofNullable(num(priceObj.get("krw"))).orElse(0.0);
        Double usd = num(priceObj.get("usd"));
        if (usd != null && usdToKrw != null) krw += usd * usdToKrw;
        return krw;
    }

    /**
     * 보유 종목 + 계좌 전체 요약(투자원금/평가금액/손익, 원화 환산 합계). GET /api/v1/holdings.
     * 종목별 손익률(rate)은 소수비율로 내려오므로 ×100 해서 % 단위로 정규화해 반환한다.
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

        Double usdToKrw = fetchUsdToKrw();

        // 계좌 합계 — totalPurchaseAmount는 Price 그 자체, marketValue/profitLoss는 한 단계 더 감싸져
        // {amount: Price, ...} 구조라 계좌합계용 파싱은 종목별 파싱과 다르게 짜야 한다.
        Map<String, Object> totalPurchasePrice = (Map<String, Object>) raw.get("totalPurchaseAmount");
        Map<String, Object> overviewMarketValue = (Map<String, Object>) raw.get("marketValue");
        Map<String, Object> overviewProfitLoss = (Map<String, Object>) raw.get("profitLoss");
        Map<String, Object> marketValuePrice = overviewMarketValue != null ? (Map<String, Object>) overviewMarketValue.get("amount") : null;
        Map<String, Object> profitLossPrice = overviewProfitLoss != null ? (Map<String, Object>) overviewProfitLoss.get("amount") : null;
        Double overviewRate = overviewProfitLoss != null ? num(overviewProfitLoss.get("rate")) : null;

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
        result.put("totalPurchaseAmount", toKrwTotal(totalPurchasePrice, usdToKrw));
        result.put("totalMarketValue", toKrwTotal(marketValuePrice, usdToKrw));
        result.put("totalProfitLossAmount", toKrwTotal(profitLossPrice, usdToKrw));
        result.put("totalProfitLossRate", overviewRate != null ? Math.round(overviewRate * 10000.0) / 100.0 : null);
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

    /**
     * 캔들 차트. GET /api/v1/candles. interval: 1m 또는 1d(토스 API가 지원하는 값은 이 둘뿐).
     * 최근 count개(최대 200). 분/일봉 외(주/월/년, N분봉)는 전부 프론트에서 이 원천 데이터를 집계한다.
     *
     * Candle의 openPrice/highPrice/lowPrice/closePrice/volume은 토스 API에서 BigDecimal을
     * JSON 숫자 문자열("16850.0000")로 내려준다. raw 그대로 프론트에 넘기면 JS에서 문자열로
     * 취급되어 차트 라이브러리 내부 연산이 깨지므로(이동평균 등에서 "+"가 숫자 덧셈이 아니라
     * 문자열 접합으로 동작 → 1e+98 같은 값 발생), 여기서 전부 Double로 확정해서 내려준다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getChart(String symbol, String interval, int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        String iv = "1m".equals(interval) ? "1m" : "1d";
        Map<String, Object> raw = unwrap(toss.get("/api/v1/candles?symbol=" + symbol + "&interval=" + iv + "&count=" + Math.min(count, 200)));
        if (raw == null) {
            result.put("available", false);
            result.put("message", "차트 데이터를 가져오지 못했습니다.");
            return result;
        }
        List<Map<String, Object>> candles = new ArrayList<>();
        for (Object o : (List<Object>) raw.getOrDefault("candles", List.of())) {
            Map<String, Object> c = (Map<String, Object>) o;
            Double open = num(c.get("openPrice"));
            Double high = num(c.get("highPrice"));
            Double low = num(c.get("lowPrice"));
            Double close = num(c.get("closePrice"));
            // 값이 하나라도 없거나 숫자로 못 바꾸면 그 봉은 통째로 버린다(차트 스케일을 깨뜨리는 원인).
            if (open == null || high == null || low == null || close == null) continue;
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("timestamp", str(c.get("timestamp")));
            n.put("openPrice", open);
            n.put("highPrice", high);
            n.put("lowPrice", low);
            n.put("closePrice", close);
            n.put("volume", num(c.get("volume")));
            candles.add(n);
        }
        result.put("available", true);
        result.put("candles", candles);
        return result;
    }

    /** 호가. GET /api/v1/orderbook. asks(매도)/bids(매수)의 price/volume도 문자열일 수 있어 숫자로 확정해 넘긴다. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getOrderbook(String symbol) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> raw = unwrap(toss.get("/api/v1/orderbook?symbol=" + symbol));
        if (raw == null) {
            result.put("available", false);
            result.put("message", "호가 데이터를 가져오지 못했습니다.");
            return result;
        }
        result.put("available", true);
        result.put("asks", normalizeOrderbookSide((List<Object>) raw.getOrDefault("asks", List.of())));
        result.put("bids", normalizeOrderbookSide((List<Object>) raw.getOrDefault("bids", List.of())));
        return result;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeOrderbookSide(List<Object> side) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object o : side) {
            Map<String, Object> it = (Map<String, Object>) o;
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("price", num(it.get("price")));
            n.put("volume", num(it.get("volume")));
            out.add(n);
        }
        return out;
    }

    /**
     * 상한가/하한가. GET /api/v1/price-limits. 공식 문서상 응답 필드명이 확정되어 있지 않아
     * 여러 후보 키를 순서대로 시도한다(넓은 후보 세트 — 실패해도 null로 조용히 넘어가고
     * 프론트는 "-"로 표시하므로 안전하다).
     */
    public Map<String, Object> getPriceLimit(String symbol) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> raw = unwrap(toss.get("/api/v1/price-limits?symbol=" + symbol));
        if (raw == null) {
            result.put("available", false);
            return result;
        }
        result.put("available", true);
        result.put("upperLimitPrice", numAny(raw, "upperLimitPrice", "upperPrice", "upperLimit", "limitUp", "ceilingPrice", "upperBound"));
        result.put("lowerLimitPrice", numAny(raw, "lowerLimitPrice", "lowerPrice", "lowerLimit", "limitDown", "floorPrice", "lowerBound"));
        return result;
    }

    /**
     * 매수 유의사항/변동성완화장치(VI) 발동 정보. GET /api/v1/stocks/{symbol}/warnings.
     * 활성 항목만 내려온다(문서 기준: startDate&lt;=오늘&lt;=endDate). VI 발동 방향(상승/하락)
     * 필드명도 공식 문서에서 확정되지 않아 여러 후보 키를 시도하고, 방향을 알 수 없으면
     * (필드가 없거나 값을 못 알아들으면) 상승/하락 둘 다 false로 둔다 — 잘못된 방향을 보여주는
     * 것보다 "-"로 비워두는 편이 안전하다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getStockWarnings(String symbol) {
        Map<String, Object> result = new LinkedHashMap<>();
        Map<String, Object> raw = toss.get("/api/v1/stocks/" + symbol + "/warnings");
        List<Map<String, Object>> list = listOf(raw, "warnings", "items", "list");
        boolean viUp = false, viDown = false;
        for (Map<String, Object> w : list) {
            String type = str(w.get("warningType"));
            if (type == null || !type.startsWith("VI_")) continue;
            String dir = null;
            for (String k : new String[]{"direction", "viDirection", "side", "triggerSide", "triggerType"}) {
                Object v = w.get(k);
                if (v != null) { dir = v.toString().toUpperCase(Locale.ROOT); break; }
            }
            if (dir != null) {
                if (dir.contains("UP") || dir.contains("RISE") || dir.contains("상승")) viUp = true;
                if (dir.contains("DOWN") || dir.contains("FALL") || dir.contains("하락") || dir.contains("하강")) viDown = true;
            }
        }
        result.put("available", raw != null);
        result.put("viUp", viUp);
        result.put("viDown", viDown);
        return result;
    }

    /**
     * 투자자별 매매동향(개인/외국인/기관/기타법인). GET /api/v1/stocks/{symbol}/investor-trading.
     * 실제 스키마(StockInvestorTradingRecord)는 date + {individual, foreigner, institution,
     * otherCorporation}이고, 각 항목은 {buyVolume, sellVolume, netBuyVolume}이다(순매수는
     * netBuyVolume 필드). 예전 코드는 buyAmount/sellAmount/netVolume 같은 존재하지 않는 필드명을
     * 찾고 있어서 값이 하나도 안 뜨는 원인이었다. count는 최신순으로 필요한 일수만 요청한다(7일).
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getInvestorTrading(String symbol, int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        int c = Math.max(1, Math.min(count, 100));
        Map<String, Object> raw = unwrap(toss.get("/api/v1/stocks/" + symbol + "/investor-trading?count=" + c));
        if (raw == null) {
            result.put("available", false);
            result.put("message", "투자자별 매매동향을 가져오지 못했습니다.");
            return result;
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object o : (List<Object>) raw.getOrDefault("records", List.of())) {
            Map<String, Object> rec = (Map<String, Object>) o;
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("date", str(rec.get("date")));
            n.put("individual", netBuyVolumeOf(mapOf(rec.get("individual"))));
            n.put("foreigner", netBuyVolumeOf(mapOf(rec.get("foreigner"))));
            n.put("institution", netBuyVolumeOf(mapOf(rec.get("institution"))));
            n.put("otherCorporation", netBuyVolumeOf(mapOf(rec.get("otherCorporation"))));
            records.add(n);
        }
        // 최신순(오늘이 맨 앞)으로 이미 내려오지만, 혹시 모를 순서 문제에 대비해 날짜 내림차순으로 한 번 더 정렬한다.
        records.sort((a, b) -> {
            String da = (String) a.get("date"), db = (String) b.get("date");
            if (da == null || db == null) return 0;
            return db.compareTo(da);
        });
        result.put("available", true);
        result.put("records", records);
        return result;
    }

    private Double netBuyVolumeOf(Map<String, Object> investor) {
        return investor == null ? null : num(investor.get("netBuyVolume"));
    }

    /**
     * 공매도 동향. GET /api/v1/stocks/{symbol}/short-selling.
     * 실제 스키마(ShortSellingRecord)는 date, shortSellingVolume(공매도 거래량),
     * shortSellingAmount(공매도 거래대금), shortSellingVolumeRate(거래량 비중, 소수 비율),
     * shortSellingAmountRate(거래대금 비중, 소수 비율)이다. 예전 코드는 volume/ratio라는
     * 존재하지 않는 필드명을 찾고 있어서 수량은 우연히(shortSellingVolume 폴백) 나왔지만
     * 비중은 항상 "-"로 보이던 원인이었다.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getShortSelling(String symbol, int count) {
        Map<String, Object> result = new LinkedHashMap<>();
        int c = Math.max(1, Math.min(count, 100));
        Map<String, Object> raw = unwrap(toss.get("/api/v1/stocks/" + symbol + "/short-selling?count=" + c));
        if (raw == null) {
            result.put("available", false);
            result.put("message", "공매도 동향을 가져오지 못했습니다.");
            return result;
        }
        List<Map<String, Object>> records = new ArrayList<>();
        for (Object o : (List<Object>) raw.getOrDefault("records", List.of())) {
            Map<String, Object> rec = (Map<String, Object>) o;
            Map<String, Object> n = new LinkedHashMap<>();
            n.put("date", str(rec.get("date")));
            n.put("shortSellingVolume", num(rec.get("shortSellingVolume")));
            n.put("shortSellingAmount", num(rec.get("shortSellingAmount")));
            n.put("shortSellingVolumeRate", num(rec.get("shortSellingVolumeRate")));
            n.put("shortSellingAmountRate", num(rec.get("shortSellingAmountRate")));
            records.add(n);
        }
        records.sort((a, b) -> {
            String da = (String) a.get("date"), db = (String) b.get("date");
            if (da == null || db == null) return 0;
            return db.compareTo(da);
        });
        result.put("available", true);
        result.put("records", records);
        return result;
    }
}
