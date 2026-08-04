package com.jihun.portfolio.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 주식 대시보드 서비스 — 토스증권 Open API + 업비트(BTC) + Gemini(AI 브리핑).
 *
 * AWS 프리티어·토스 레이트리밋 보호를 위해 모든 외부 호출은 서버 측 TTL 캐시를 거친다:
 *  - 지표/랭킹 60초, 수급 10분, 장 캘린더 6시간, AI 브리핑 30분.
 * 프론트가 아무리 자주 새로고침해도 TTL 안에선 캐시가 반환되므로 외부 API 호출량이 제한된다.
 *
 * 토스 응답 스키마는 버전에 따라 필드명이 다를 수 있어, 후보 키 목록에서 첫 번째로 존재하는 값을
 * 꺼내는 방어적 파싱(pickNum/pickStr)으로 처리한다. 필드가 없으면 null로 내려보내고 프론트가 숨긴다.
 */
@Service
public class StockDashboardService {

    private static final Logger log = LoggerFactory.getLogger(StockDashboardService.class);

    private final TossApiClient toss;
    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private record CacheEntry(long expiresAt, Object value) {}

    public StockDashboardService(TossApiClient toss) {
        this.toss = toss;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4000);
        f.setReadTimeout(15000);
        this.rest = new RestTemplate(f);
    }

    @SuppressWarnings("unchecked")
    private <T> T cached(String key, long ttlMillis, java.util.function.Supplier<T> loader) {
        CacheEntry e = cache.get(key);
        long now = System.currentTimeMillis();
        if (e != null && now < e.expiresAt()) return (T) e.value();
        T value = loader.get();
        if (value != null) cache.put(key, new CacheEntry(now + ttlMillis, value));
        return value;
    }

    /* ===================== 공통 방어적 파싱 유틸 ===================== */

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrap(Map<String, Object> res) {
        if (res == null) return null;
        Object r = res.get("result");
        if (r instanceof Map) return (Map<String, Object>) r;
        return res;
    }

    private static Double pickNum(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof Number n) return n.doubleValue();
            if (v instanceof String s) { try { return Double.parseDouble(s); } catch (Exception ignored) {} }
        }
        return null;
    }

    private static String pickStr(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof String s && !s.isBlank()) return s;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pickList(Map<String, Object> m, String... keys) {
        if (m == null) return List.of();
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof List) return (List<Map<String, Object>>) v;
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> pickMap(Map<String, Object> m, String... keys) {
        if (m == null) return null;
        for (String k : keys) {
            Object v = m.get(k);
            if (v instanceof Map) return (Map<String, Object>) v;
        }
        return null;
    }

    /* ===================== 상단 지표 ===================== */

    public Map<String, Object> getIndicators() {
        return cached("indicators", 60_000, this::loadIndicators);
    }

    private Map<String, Object> loadIndicators() {
        List<Map<String, Object>> items = new ArrayList<>();

        // 국내 지수 (토스 시장지표)
        Map<String, Object> idxRes = unwrap(toss.get("/api/v1/market-indicators/prices?symbols=KOSPI,KOSDAQ"));
        List<Map<String, Object>> idxList = idxRes == null ? List.of() : pickList(idxRes, "prices", "items", "indicators", "list");
        for (Map<String, Object> p : idxList) {
            String symbol = pickStr(p, "symbol", "code");
            items.add(indicator(symbol, "KOSPI".equals(symbol) ? "코스피" : "코스닥",
                    pickNum(p, "close", "price", "currentPrice", "value", "last"),
                    pickNum(p, "changeRate", "changePercent", "rate"),
                    pickNum(p, "change", "changeValue", "changePrice"),
                    "KRW", sparkFromTossIndicator(symbol)));
        }

        // 미국 지수 프록시 ETF (토스 미국 주식 시세) — 지수 자체는 미제공이라 ETF로 대신 표시하고 라벨에 명시
        String[][] usProxies = {{"SPY", "S&P 500 (SPY)"}, {"QQQ", "나스닥100 (QQQ)"}, {"DIA", "다우존스 (DIA)"}};
        Map<String, Object> usRes = unwrap(toss.get("/api/v1/prices?symbols=SPY,QQQ,DIA"));
        List<Map<String, Object>> usList = usRes == null ? List.of() : pickList(usRes, "prices", "items", "list");
        for (String[] proxy : usProxies) {
            Map<String, Object> found = null;
            for (Map<String, Object> p : usList) {
                if (proxy[0].equals(pickStr(p, "symbol", "code"))) { found = p; break; }
            }
            if (found != null) {
                items.add(indicator(proxy[0], proxy[1],
                        pickNum(found, "close", "price", "currentPrice", "last", "tradePrice"),
                        pickNum(found, "changeRate", "changePercent", "rate"),
                        pickNum(found, "change", "changeValue", "changePrice"),
                        "USD", null));
            }
        }

        // 환율 (토스)
        Map<String, Object> fxRes = unwrap(toss.get("/api/v1/exchange-rate"));
        if (fxRes != null) {
            Double rate = pickNum(fxRes, "rate", "exchangeRate", "baseRate", "krwPerUsd", "close", "price", "value");
            if (rate == null) {
                Map<String, Object> inner = pickMap(fxRes, "exchangeRate", "usd", "USD");
                rate = pickNum(inner, "rate", "baseRate", "close", "price", "value");
            }
            items.add(indicator("USDKRW", "달러 환율", rate,
                    pickNum(fxRes, "changeRate", "changePercent"), pickNum(fxRes, "change", "changeValue"), "KRW", null));
        }

        // 비트코인 (업비트 공개 API — 키 불필요)
        try {
            String body = rest.getForObject("https://api.upbit.com/v1/ticker?markets=KRW-BTC", String.class);
            List<Map<String, Object>> arr = mapper.readValue(body, List.class);
            if (!arr.isEmpty()) {
                Map<String, Object> btc = arr.get(0);
                items.add(indicator("BTC", "비트코인",
                        pickNum(btc, "trade_price"),
                        pickNum(btc, "signed_change_rate") == null ? null : pickNum(btc, "signed_change_rate") * 100,
                        pickNum(btc, "signed_change_price"), "KRW", sparkFromUpbit()));
            }
        } catch (Exception e) {
            log.warn("업비트 BTC 조회 실패: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("items", items);
        result.put("updatedAt", System.currentTimeMillis());
        result.put("tossConfigured", toss.isConfigured());
        return result;
    }

    private Map<String, Object> indicator(String symbol, String label, Double price, Double changeRate, Double change, String currency, List<Double> spark) {
        Map<String, Object> m = new HashMap<>();
        m.put("symbol", symbol); m.put("label", label); m.put("price", price);
        m.put("changeRate", changeRate); m.put("change", change); m.put("currency", currency); m.put("spark", spark);
        return m;
    }

    private List<Double> sparkFromTossIndicator(String symbol) {
        if (symbol == null) return null;
        Map<String, Object> res = unwrap(toss.get("/api/v1/market-indicators/" + symbol + "/candles?interval=1m&count=60"));
        return closesOf(res);
    }

    private List<Double> closesOf(Map<String, Object> res) {
        if (res == null) return null;
        List<Map<String, Object>> candles = pickList(res, "candles", "items", "list");
        if (candles.isEmpty()) return null;
        List<Double> closes = new ArrayList<>();
        for (Map<String, Object> c : candles) {
            Double close = pickNum(c, "close", "closePrice", "tradePrice", "price");
            if (close != null) closes.add(close);
        }
        Collections.reverse(closes); // 최신순 → 시간순
        return closes.isEmpty() ? null : closes;
    }

    @SuppressWarnings("unchecked")
    private List<Double> sparkFromUpbit() {
        try {
            String body = rest.getForObject("https://api.upbit.com/v1/candles/minutes/30?market=KRW-BTC&count=48", String.class);
            List<Map<String, Object>> arr = mapper.readValue(body, List.class);
            List<Double> closes = new ArrayList<>();
            for (Map<String, Object> c : arr) {
                Double v = pickNum(c, "trade_price");
                if (v != null) closes.add(v);
            }
            Collections.reverse(closes);
            return closes.isEmpty() ? null : closes;
        } catch (Exception e) { return null; }
    }

    /* ===================== 장 운영 캘린더 (향후 7일) ===================== */

    public Map<String, Object> getCalendar() {
        return cached("calendar", 6 * 3600_000, () -> {
            Map<String, Object> result = new HashMap<>();
            result.put("kr", pickList(unwrap(toss.get("/api/v1/market-calendar/KR")), "days", "items", "calendar", "list"));
            result.put("us", pickList(unwrap(toss.get("/api/v1/market-calendar/US")), "days", "items", "calendar", "list"));
            return result;
        });
    }

    /* ===================== 수급 (투자자별 매매대금) ===================== */

    public Map<String, Object> getInvestorTrading() {
        return cached("investor", 10 * 60_000, () -> {
            Map<String, Object> result = new HashMap<>();
            for (String symbol : List.of("KOSPI", "KOSDAQ")) {
                Map<String, Object> res = unwrap(toss.get("/api/v1/market-indicators/" + symbol + "/investor-trading?interval=1d&count=1"));
                List<Map<String, Object>> records = pickList(res, "records", "items", "list");
                if (!records.isEmpty()) {
                    Map<String, Object> rec = records.get(0);
                    Map<String, Object> net = new HashMap<>();
                    net.put("date", pickStr(rec, "date", "tradingDate", "dt"));
                    for (String investor : List.of("individual", "foreigner", "institution")) {
                        Map<String, Object> inv = pickMap(rec, investor);
                        Double buy = pickNum(inv, "buyAmount", "buy");
                        Double sell = pickNum(inv, "sellAmount", "sell");
                        net.put(investor, (buy != null && sell != null) ? buy - sell : null);
                    }
                    result.put(symbol, net);
                }
            }
            return result;
        });
    }

    /* ===================== 랭킹 ===================== */

    /** tab: amount(거래대금) / gainers(급상승) / losers(급하락), country: KR / US */
    public Map<String, Object> getRankings(String country, String tab) {
        String c = "US".equalsIgnoreCase(country) ? "US" : "KR";
        String type, duration;
        switch (tab == null ? "amount" : tab) {
            case "gainers" -> { type = "TOP_GAINERS"; duration = "1d"; }   // TOP_*는 realtime 미지원
            case "losers" -> { type = "TOP_LOSERS"; duration = "1d"; }
            default -> { type = "MARKET_TRADING_AMOUNT"; duration = "realtime"; }
        }
        final String fc = c, ft = type, fd = duration;
        return cached("rank:" + fc + ":" + ft, 60_000, () -> {
            Map<String, Object> res = unwrap(toss.get("/api/v1/rankings?type=" + ft + "&marketCountry=" + fc
                    + "&duration=" + fd + "&count=30&excludeInvestmentCaution=false"));
            List<Map<String, Object>> rankings = pickList(res, "rankings", "items", "list");
            List<Map<String, Object>> normalized = new ArrayList<>();
            int rank = 1;
            for (Map<String, Object> item : rankings) {
                Map<String, Object> price = pickMap(item, "price");
                Map<String, Object> n = new HashMap<>();
                n.put("rank", pickNum(item, "rank") != null ? pickNum(item, "rank").intValue() : rank);
                n.put("symbol", pickStr(item, "symbol", "code"));
                n.put("name", pickStr(item, "name", "stockName", "koreanName"));
                n.put("price", pickNum(price != null ? price : item, "close", "current", "currentPrice", "price", "last", "tradePrice"));
                n.put("changeRate", pickNum(price != null ? price : item, "changeRate", "changePercent", "rate"));
                n.put("tradingAmount", pickNum(item, "tradingAmount", "tradingValue", "amount"));
                n.put("tradingVolume", pickNum(item, "tradingVolume", "volume"));
                n.put("marketCap", pickNum(item, "marketCap", "marketCapitalization"));
                n.put("currency", "US".equals(fc) ? "USD" : "KRW");
                normalized.add(n);
                rank++;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("rankings", normalized);
            result.put("rankedAt", res == null ? null : res.get("rankedAt"));
            result.put("updatedAt", System.currentTimeMillis());
            return result;
        });
    }

    /* ===================== AI 시황 브리핑 (Gemini) ===================== */

    public Map<String, Object> getAiBriefing() {
        return cached("briefing", 30 * 60_000, this::loadAiBriefing);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadAiBriefing() {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("summary", "AI 브리핑을 생성하지 못했습니다. 잠시 후 다시 시도해주세요.");
        fallback.put("weekAhead", List.of());
        fallback.put("picks", List.of());
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            fallback.put("summary", "GEMINI_API_KEY가 설정되지 않아 AI 브리핑을 생성할 수 없습니다.");
            return fallback;
        }
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("indicators", getIndicators().get("items"));
            context.put("investorTrading", getInvestorTrading());
            context.put("krTopAmount", getRankings("KR", "amount").get("rankings"));
            context.put("usTopAmount", getRankings("US", "amount").get("rankings"));
            String contextJson = mapper.writeValueAsString(context);
            if (contextJson.length() > 14000) contextJson = contextJson.substring(0, 14000);

            String prompt = "당신은 증권사 리서치센터의 애널리스트입니다. 아래 실시간 시장 데이터(JSON)를 바탕으로 한국어로 시황을 요약하세요.\n"
                    + "반드시 순수 JSON만 출력하세요(마크다운 백틱 금지). 스키마:\n"
                    + "{\"summary\": \"오늘 시황 요약 3~5문장 (지수 흐름, 수급 특징, 주도 섹터)\",\n"
                    + " \"weekAhead\": [\"이번 주 주목할 일정이나 이벤트 3~5개 (일반적 경제 캘린더 지식 기반, 날짜 불확실하면 '이번 주' 수준으로)\"],\n"
                    + " \"picks\": [{\"name\": \"종목명\", \"symbol\": \"심볼\", \"market\": \"KR또는US\", \"reason\": \"제공된 거래대금 상위 목록에서 고른 이유 1~2문장\"}] (3~5개, 반드시 제공된 목록 안에서만)}\n"
                    + "투자 권유가 아닌 데이터 기반 관찰만 서술하세요.\n\n데이터:\n" + contextJson;

            Map<String, Object> reqBody = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key=" + geminiApiKey;
            ResponseEntity<String> res = rest.postForEntity(url, new HttpEntity<>(mapper.writeValueAsString(reqBody), headers), String.class);
            Map<String, Object> body = mapper.readValue(res.getBody(), Map.class);
            List<Map<String, Object>> candidates = pickList(body, "candidates");
            if (candidates.isEmpty()) return fallback;
            Map<String, Object> content = pickMap(candidates.get(0), "content");
            List<Map<String, Object>> parts = pickList(content, "parts");
            if (parts.isEmpty()) return fallback;
            String text = pickStr(parts.get(0), "text");
            if (text == null) return fallback;
            String cleaned = text.replaceAll("```json|```", "").trim();
            Map<String, Object> parsed = mapper.readValue(cleaned, Map.class);
            parsed.put("generatedAt", System.currentTimeMillis());
            return parsed;
        } catch (Exception e) {
            log.error("AI 브리핑 생성 실패: {}", e.getMessage());
            return fallback;
        }
    }
}
