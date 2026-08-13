package com.jihun.portfolio.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 주식 대시보드 서비스 — 토스증권 Open API + 업비트(BTC) + Gemini(AI 브리핑).
 *
 * 보호장치 3중:
 *  1) TossApiClient 전역 스로틀(300ms 간격) + 429 재시도
 *  2) TTL 캐시: 지표/랭킹 60초, 수급 10분, 캘린더 12시간(서버 재시작 시에도 새로 채워짐), AI 브리핑 12시간(실패 시 10분 후 재시도), 종목마스터 24시간, 종목별 AI 요약 12시간, 종목 전체 목록(검색용) 24시간
 *  3) 요청 병합: cached()가 동기화되어 같은 데이터를 동시에 중복 로드하지 않음
 *
 * 스키마(공식 문서 확인):
 *  - RankingItem: rank/symbol/currency/price{lastPrice,basePrice,changeRate(소수비율)}/tradingVolume/tradingAmount
 *    → 종목명·시총은 없음. /api/v1/stocks(종목마스터: name, sharesOutstanding)를 조인해 시총 계산.
 *  - Stock Info 카테고리는 공식 커버리지상 "stock master data and stock warnings"뿐이라 업종/섹터 필드는 없음
 *    (developers.tossinvest.com/llms.txt 확인). 업종은 종목별 AI 요약에서 Gemini가 함께 추정한다.
 *  - MarketIndicatorPriceResponse / PriceResponse: lastPrice만 있고 등락률 없음 → 일봉 2개로 전일대비 계산.
 *  - exchange-rate: baseCurrency=USD&quoteCurrency=KRW 필수. 등락률 필드가 없어 자체 수집 이력으로 당일 대비를 계산한다.
 *  - 캘린더는 전일/당일/익일 3영업일만 반환한다(토스 API 자체 제약) — 프론트가 향후 7일을 훑어도 실제로는
 *    최대 1영업일치 휴장 정보만 나올 수 있다. 더 긴 범위를 원하면 별도 공휴일 데이터가 필요하다.
 *  - 종목명 검색: 토스 API에는 이름 기반 검색 엔드포인트가 따로 없어, 마켓별 전체 종목 목록
 *    (GET /api/v1/stocks/all, symbol+name)을 24시간 캐시해두고 그 안에서 부분 문자열로 직접 찾는다.
 *  - 검색/즐겨찾기의 거래대금: 랭킹 API(/api/v1/rankings)만 실제 누적 거래대금(tradingAmount)을 주고,
 *    임의 종목 하나를 콕 집어 조회하는 API는 그 필드가 없다. 대신 일봉의 당일 거래량(volume)에 현재가를
 *    곱해 근사치를 계산한다(체결 시점별 가격을 다 더한 값이 아니라 "현재가 × 거래량"이라 랭킹의 정확한
 *    거래대금과는 약간 다를 수 있음 — 정렬용이 아니라 참고용 표시이므로 이 정도 근사로 충분하다고 판단).
 */
@Service
public class StockDashboardService {

    private static final Logger log = LoggerFactory.getLogger(StockDashboardService.class);
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final TossApiClient toss;
    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private volatile String workingGeminiModel = null;

    /** 환율은 등락률 API가 없어 서버가 직접 당일 샘플을 모아 시가 대비 등락률·스파크라인을 만든다. */
    private final Deque<double[]> fxHistory = new ArrayDeque<>(); // {epochDay, rate}

    @Value("${GEMINI_API_KEY:}")
    private String geminiApiKey;

    private record CacheEntry(long expiresAt, Object value) {}

    public StockDashboardService(TossApiClient toss) {
        this.toss = toss;
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4000);
        f.setReadTimeout(20000);
        this.rest = new RestTemplate(f);
    }

    /** 동기화로 직렬화해 동일 키 중복 로드(요청 병합)와 버스트 호출을 막는다. 느려도 괜찮다는 요구사항 반영. */
    @SuppressWarnings("unchecked")
    private synchronized <T> T cached(String key, long ttlMillis, java.util.function.Supplier<T> loader) {
        CacheEntry e = cache.get(key);
        long now = System.currentTimeMillis();
        if (e != null && now < e.expiresAt()) return (T) e.value();
        T value = loader.get();
        if (value != null) cache.put(key, new CacheEntry(System.currentTimeMillis() + ttlMillis, value));
        return value;
    }

    /* ===================== 파싱 유틸 ===================== */

    @SuppressWarnings("unchecked")
    private static Map<String, Object> unwrap(Map<String, Object> res) {
        if (res == null) return null;
        Object r = res.get("result");
        if (r instanceof Map) return (Map<String, Object>) r;
        return res;
    }

    /** result가 배열이거나, result/최상위 내 후보 키 아래 배열인 경우를 모두 처리 */
    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> listOf(Map<String, Object> raw, String... keys) {
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

        // 국내 지수: 현재가(lastPrice만 제공) + 일봉 2개로 전일대비 계산 + 1분봉 스파크라인
        Map<String, Object> idxRes = toss.get("/api/v1/market-indicators/prices?symbols=KOSPI,KOSDAQ");
        for (Map<String, Object> p : listOf(idxRes, "prices", "items", "indicators", "list")) {
            String symbol = pickStr(p, "symbol");
            Double last = pickNum(p, "lastPrice", "close", "price");
            Double changeRate = null, change = null;
            List<Double> daily = closesOf(toss.get("/api/v1/market-indicators/" + symbol + "/candles?interval=1d&count=2"));
            if (last != null && daily != null && daily.size() >= 2) {
                double prev = daily.get(daily.size() - 2);
                if (prev != 0) { change = last - prev; changeRate = (last - prev) / prev * 100; }
            }
            List<Double> spark = closesOf(toss.get("/api/v1/market-indicators/" + symbol + "/candles?interval=1m&count=60"));
            items.add(indicator(symbol, "KOSPI".equals(symbol) ? "코스피" : "코스닥", last, changeRate, change, "KRW", spark));
        }

        // 미국 지수 프록시 ETF: 현재가(PriceResponse.lastPrice) + 일봉 2개 전일대비 + 1분봉 스파크라인
        String[][] usProxies = {{"SPY", "S&P 500 (SPY)"}, {"QQQ", "나스닥100 (QQQ)"}, {"DIA", "다우존스 (DIA)"}};
        Map<String, Object> usRes = toss.get("/api/v1/prices?symbols=SPY,QQQ,DIA");
        List<Map<String, Object>> usList = listOf(usRes, "prices", "items", "list");
        for (String[] proxy : usProxies) {
            Map<String, Object> found = null;
            for (Map<String, Object> p : usList) {
                if (proxy[0].equals(pickStr(p, "symbol"))) { found = p; break; }
            }
            if (found == null) continue;
            Double last = pickNum(found, "lastPrice", "close", "price");
            Double changeRate = null, change = null;
            List<Double> daily = closesOf(toss.get("/api/v1/candles?symbol=" + proxy[0] + "&interval=1d&count=2"));
            if (last != null && daily != null && daily.size() >= 2) {
                double prev = daily.get(daily.size() - 2);
                if (prev != 0) { change = last - prev; changeRate = (last - prev) / prev * 100; }
            }
            List<Double> spark = closesOf(toss.get("/api/v1/candles?symbol=" + proxy[0] + "&interval=1m&count=60"));
            items.add(indicator(proxy[0], proxy[1], last, changeRate, change, "USD", spark));
        }

        // 환율: baseCurrency/quoteCurrency 필수 파라미터. 등락률 API가 없어 당일 첫 샘플 대비로 직접 계산.
        Map<String, Object> fxRes = unwrap(toss.get("/api/v1/exchange-rate?baseCurrency=USD&quoteCurrency=KRW"));
        if (fxRes != null) {
            Double rate = pickNum(fxRes, "rate", "exchangeRate", "value", "price", "lastPrice");
            if (rate != null) {
                double[] fxChange = recordFxSample(rate);
                items.add(indicator("USDKRW", "달러 환율", rate, fxChange[0], fxChange[1], "KRW", fxSpark()));
            }
        }

        // 비트코인 (업비트 공개 API)
        try {
            String body = rest.getForObject("https://api.upbit.com/v1/ticker?markets=KRW-BTC", String.class);
            List<Map<String, Object>> arr = mapper.readValue(body, List.class);
            if (!arr.isEmpty()) {
                Map<String, Object> btc = arr.get(0);
                Double scr = pickNum(btc, "signed_change_rate");
                items.add(indicator("BTC", "비트코인", pickNum(btc, "trade_price"),
                        scr == null ? null : scr * 100, pickNum(btc, "signed_change_price"), "KRW", sparkFromUpbit()));
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

    /** 당일(KST) 첫 샘플을 기준가로 삼아 {changeRate, change}를 반환하고, 자정이 지나면 이력을 초기화한다. */
    private synchronized double[] recordFxSample(double rate) {
        long today = LocalDate.now(KST).toEpochDay();
        if (!fxHistory.isEmpty() && (long) fxHistory.peekFirst()[0] != today) fxHistory.clear();
        fxHistory.addLast(new double[]{today, rate});
        while (fxHistory.size() > 200) fxHistory.removeFirst();
        double base = fxHistory.peekFirst()[1];
        double change = rate - base;
        double changeRate = base != 0 ? change / base * 100 : 0;
        return new double[]{changeRate, change};
    }

    private synchronized List<Double> fxSpark() {
        if (fxHistory.size() < 2) return null;
        List<Double> spark = new ArrayList<>();
        for (double[] s : fxHistory) spark.add(s[1]);
        return spark;
    }

    private Map<String, Object> indicator(String symbol, String label, Double price, Double changeRate, Double change, String currency, List<Double> spark) {
        Map<String, Object> m = new HashMap<>();
        m.put("symbol", symbol); m.put("label", label); m.put("price", price);
        m.put("changeRate", changeRate); m.put("change", change); m.put("currency", currency); m.put("spark", spark);
        return m;
    }

    private List<Double> closesOf(Map<String, Object> raw) {
        List<Map<String, Object>> candles = listOf(raw, "candles", "items", "list");
        if (candles.isEmpty()) return null;
        List<Double> closes = new ArrayList<>();
        for (Map<String, Object> c : candles) {
            Double close = pickNum(c, "close", "closePrice", "tradePrice", "price", "lastPrice");
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

    /* ===================== 장 운영 캘린더 ===================== */

    /** 서버 재시작 시 + 12시간마다만 토스에 재요청한다(체감상 자주 새로고침되는 느낌을 주지 않기 위함). */
    public Map<String, Object> getCalendar() {
        return cached("calendar", 12 * 3600_000, () -> {
            Map<String, Object> result = new HashMap<>();
            result.put("kr", listOf(toss.get("/api/v1/market-calendar/KR"), "days", "items", "calendar", "list"));
            result.put("us", listOf(toss.get("/api/v1/market-calendar/US"), "days", "items", "calendar", "list"));
            return result;
        });
    }

    /* ===================== 수급 ===================== */

    public Map<String, Object> getInvestorTrading() {
        return cached("investor", 10 * 60_000, () -> {
            Map<String, Object> result = new HashMap<>();
            for (String symbol : List.of("KOSPI", "KOSDAQ")) {
                Map<String, Object> raw = toss.get("/api/v1/market-indicators/" + symbol + "/investor-trading?interval=1d&count=1");
                List<Map<String, Object>> records = listOf(raw, "records", "items", "list");
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

    /* ===================== 종목 마스터 (이름·발행주식수) ===================== */

    /**
     * 종목명·발행주식수는 거의 안 바뀌므로 24시간 캐시. 랭킹에 종목명·시총을 붙이는 데 사용.
     * (참고) 토스 Open API 공식 문서(developers.tossinvest.com/llms.txt)의 API 커버리지에는
     * "Stock Info: stock master data and stock warnings"라고만 명시되어 있어, 업종/섹터 분류
     * 필드는 공식 API 범위에 없다. 그래서 업종은 이 마스터가 아니라 종목별 AI 요약(getStockInsight)에서
     * Gemini가 함께 추정해 채워준다.
     */
    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> stockMaster(List<String> symbols) {
        if (symbols.isEmpty()) return Map.of();
        List<String> sorted = new ArrayList<>(new TreeSet<>(symbols));
        String key = "stocks:" + String.join(",", sorted);
        return cached(key, 24 * 3600_000, () -> {
            Map<String, Map<String, Object>> master = new HashMap<>();
            Map<String, Object> raw = toss.get("/api/v1/stocks?symbols=" + String.join(",", sorted));
            for (Map<String, Object> s : listOf(raw, "stocks", "items", "list")) {
                String sym = pickStr(s, "symbol");
                if (sym == null) continue;
                Map<String, Object> info = new HashMap<>();
                info.put("name", pickStr(s, "name", "englishName"));
                info.put("sharesOutstanding", pickNum(s, "sharesOutstanding"));
                master.put(sym, info);
            }
            return master.isEmpty() ? null : master;
        });
    }

    /* ===================== 랭킹 ===================== */

    public Map<String, Object> getRankings(String country, String tab) {
        String c = "US".equalsIgnoreCase(country) ? "US" : "KR";
        String type, duration;
        switch (tab == null ? "amount" : tab) {
            case "gainers" -> { type = "TOP_GAINERS"; duration = "1d"; }
            case "losers" -> { type = "TOP_LOSERS"; duration = "1d"; }
            default -> { type = "MARKET_TRADING_AMOUNT"; duration = "realtime"; }
        }
        final String fc = c, ft = type, fd = duration;
        return cached("rank:" + fc + ":" + ft, 60_000, () -> {
            Map<String, Object> raw = toss.get("/api/v1/rankings?type=" + ft + "&marketCountry=" + fc
                    + "&duration=" + fd + "&count=30&excludeInvestmentCaution=false");
            List<Map<String, Object>> rankings = listOf(raw, "rankings", "items", "list");

            // 종목명·발행주식수 조인 (랭킹 응답엔 symbol만 있음)
            List<String> symbols = new ArrayList<>();
            for (Map<String, Object> item : rankings) {
                String s = pickStr(item, "symbol");
                if (s != null) symbols.add(s);
            }
            Map<String, Map<String, Object>> master = stockMaster(symbols);
            if (master == null) master = Map.of();

            List<Map<String, Object>> normalized = new ArrayList<>();
            int fallbackRank = 1;
            for (Map<String, Object> item : rankings) {
                String symbol = pickStr(item, "symbol");
                Map<String, Object> price = pickMap(item, "price");
                Double last = pickNum(price, "lastPrice");
                Double rateRatio = pickNum(price, "changeRate"); // 소수비율 (0.0125 = 1.25%)
                Map<String, Object> info = master.get(symbol);
                Double shares = info == null ? null : pickNum(info, "sharesOutstanding");

                Map<String, Object> n = new HashMap<>();
                Double rank = pickNum(item, "rank");
                n.put("rank", rank != null ? rank.intValue() : fallbackRank);
                n.put("symbol", symbol);
                n.put("name", info == null ? null : pickStr(info, "name"));
                n.put("price", last);
                n.put("changeRate", rateRatio == null ? null : rateRatio * 100);
                n.put("tradingAmount", pickNum(item, "tradingAmount"));
                n.put("tradingVolume", pickNum(item, "tradingVolume"));
                n.put("marketCap", (shares != null && last != null) ? shares * last : null);
                n.put("currency", pickStr(item, "currency") != null ? pickStr(item, "currency") : ("US".equals(fc) ? "USD" : "KRW"));
                normalized.add(n);
                fallbackRank++;
            }
            Map<String, Object> result = new HashMap<>();
            result.put("rankings", normalized);
            result.put("updatedAt", System.currentTimeMillis());
            return result;
        });
    }

    /* ===================== 종목 검색 · 즐겨찾기 시세 조회 =====================
     * 로그인 기능이 없는 공개 페이지라 즐겨찾기는 서버가 아니라 브라우저(localStorage)에 저장한다
     * (IP나 쿠키로 사용자를 구분하는 건 오해의 소지가 크다 — IP는 같은 와이파이·사무실에서 겹치고,
     * 쿠키도 결국 이 브라우저에서만 유효해 localStorage와 실질적 차이가 없다). 서버는 "이 심볼들의
     * 최신 시세를 달라"는 조회만 담당한다.
     */

    /**
     * 마켓별 전체 종목 목록(symbol+name). 종목명 자체는 거의 안 바뀌므로 24시간 캐시.
     * 토스 API에 이름 검색 엔드포인트가 따로 없어, 이 목록을 통째로 받아 서버 메모리에서
     * 부분 문자열로 직접 찾는 방식으로 검색을 구현한다.
     *
     * [주의] GET /api/v1/stocks/all은 marketCountry가 아니라 market 파라미터를 받고,
     * 값도 KR/US가 아니라 거래소 단위(KOSPI/KOSDAQ/NYSE/NASDAQ/AMEX/KR_ETC/US_ETC)다
     * (한 번에 하나의 시장만 조회 가능 — 나라 단위가 아님). 나라별로 관련 거래소를 모두 순회해 합친다.
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> stockUniverse(String country) {
        String key = "universe:" + country;
        List<Map<String, Object>> result = cached(key, 24 * 3600_000, () -> {
            String[] markets = "US".equals(country)
                    ? new String[]{"NYSE", "NASDAQ", "AMEX"}
                    : new String[]{"KOSPI", "KOSDAQ"};
            List<Map<String, Object>> out = new ArrayList<>();
            for (String market : markets) {
                Map<String, Object> raw = toss.get("/api/v1/stocks/all?market=" + market);
                List<Map<String, Object>> list = listOf(raw, "stocks", "items", "list");
                for (Map<String, Object> s : list) {
                    String sym = pickStr(s, "symbol");
                    String name = pickStr(s, "name");
                    if (sym == null || name == null) continue;
                    Map<String, Object> n = new HashMap<>();
                    n.put("symbol", sym);
                    n.put("name", name);
                    out.add(n);
                }
            }
            return out.isEmpty() ? null : out;
        });
        return result == null ? List.of() : result;
    }

    /** 여러 심볼의 현재가를 한 번에 조회(배치 호출). */
    private Map<String, Double> fetchPrices(List<String> symbols, String country) {
        if (symbols.isEmpty()) return Map.of();
        Map<String, Object> raw = toss.get("/api/v1/prices?symbols=" + String.join(",", symbols));
        Map<String, Double> out = new HashMap<>();
        for (Map<String, Object> p : listOf(raw, "prices", "items", "list")) {
            String sym = pickStr(p, "symbol");
            Double last = pickNum(p, "lastPrice", "close", "price");
            if (sym != null) out.put(sym, last);
        }
        return out;
    }

    /**
     * 일봉 2개(전일·당일)를 한 번에 받아 {changeRate, volume}을 계산한다.
     * changeRate: PriceResponse엔 등락률 필드가 없어서(위 클래스 설명 참고) 전일 종가 대비로 직접 계산.
     * volume: 당일(가장 최근) 봉의 거래량 — 검색/즐겨찾기의 "거래대금" 근사치(price × volume) 계산에 사용.
     * 검색·즐겨찾기처럼 종목 수가 적은 곳에서만 종목당 1회씩 호출한다(랭킹처럼 30개씩 매 60초 부르면 과함).
     */
    private Map<String, Double> fetchDailyStats(String symbol) {
        Map<String, Object> raw = toss.get("/api/v1/candles?symbol=" + symbol + "&interval=1d&count=2");
        List<Map<String, Object>> candles = listOf(raw, "candles", "items", "list");
        Map<String, Double> out = new HashMap<>();
        if (candles.isEmpty()) return out;
        // 토스 캔들 응답은 최신순(가장 최근 봉이 0번)
        Double todayClose = pickNum(candles.get(0), "closePrice");
        Double todayVolume = pickNum(candles.get(0), "volume");
        if (todayVolume != null) out.put("volume", todayVolume);
        if (candles.size() >= 2) {
            Double prevClose = pickNum(candles.get(1), "closePrice");
            if (todayClose != null && prevClose != null && prevClose != 0) {
                out.put("changeRate", (todayClose - prevClose) / prevClose * 100);
            }
        }
        return out;
    }

    /** matched 종목 목록에 현재가·등락률·거래대금(근사)·시가총액을 붙여 랭킹 행과 동일한 모양으로 만든다. */
    private List<Map<String, Object>> enrichQuotes(List<Map<String, Object>> matched, String country) {
        if (matched.isEmpty()) return List.of();
        List<String> symbols = matched.stream().map(s -> (String) s.get("symbol")).toList();
        Map<String, Double> prices = fetchPrices(symbols, country);
        Map<String, Map<String, Object>> master = stockMaster(symbols);
        if (master == null) master = Map.of();
        String currency = "US".equals(country) ? "USD" : "KRW";

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map<String, Object> s : matched) {
            String sym = (String) s.get("symbol");
            Double price = prices.get(sym);
            Map<String, Double> stats = fetchDailyStats(sym);
            Double changeRate = stats.get("changeRate");
            Double volume = stats.get("volume");
            Map<String, Object> info = master.get(sym);
            Double shares = info == null ? null : pickNum(info, "sharesOutstanding");
            String name = (String) s.get("name");
            if (name == null && info != null) name = pickStr(info, "name");

            Map<String, Object> n = new HashMap<>();
            n.put("symbol", sym);
            n.put("name", name);
            n.put("price", price);
            n.put("changeRate", changeRate);
            // 정확한 누적 거래대금은 랭킹 API에만 있어서, 당일 거래량 × 현재가로 근사치를 낸다(참고용 표시).
            n.put("tradingAmount", (price != null && volume != null) ? price * volume : null);
            n.put("marketCap", (shares != null && price != null) ? shares * price : null);
            n.put("currency", currency);
            results.add(n);
        }
        return results;
    }

    /**
     * 종목명(부분 문자열) 검색. 심볼로도 매칭한다. 결과가 많을 수 있어 이름이 검색어로 시작하는
     * 항목을 우선하고 최대 10개만 시세를 붙여 반환한다(시세 조회가 종목당 API 호출을 유발하므로
     * 너무 많이 붙이지 않는다).
     */
    public Map<String, Object> searchStocks(String query, String country) {
        String c = "US".equalsIgnoreCase(country) ? "US" : "KR";
        String q = query == null ? "" : query.trim();
        Map<String, Object> empty = new HashMap<>();
        empty.put("results", List.of());
        if (q.isEmpty()) return empty;

        String qLower = q.toLowerCase(Locale.ROOT);
        List<Map<String, Object>> matched = stockUniverse(c).stream()
                .filter(s -> {
                    String name = (String) s.get("name");
                    String sym = (String) s.get("symbol");
                    return (name != null && name.toLowerCase(Locale.ROOT).contains(qLower))
                            || (sym != null && sym.toLowerCase(Locale.ROOT).contains(qLower));
                })
                .sorted((a, b) -> {
                    String an = String.valueOf(a.get("name"));
                    String bn = String.valueOf(b.get("name"));
                    boolean aStarts = an.toLowerCase(Locale.ROOT).startsWith(qLower);
                    boolean bStarts = bn.toLowerCase(Locale.ROOT).startsWith(qLower);
                    if (aStarts != bStarts) return aStarts ? -1 : 1;
                    return Integer.compare(an.length(), bn.length()); // 더 짧은 이름(더 정확한 매치일 가능성)을 우선
                })
                .limit(10)
                .collect(Collectors.toList());

        if (matched.isEmpty()) return empty;
        Map<String, Object> result = new HashMap<>();
        result.put("results", enrichQuotes(matched, c));
        return result;
    }

    /** 즐겨찾기 화면용 — 브라우저가 들고 있는 심볼 목록의 최신 시세를 한 번에 돌려준다. */
    public Map<String, Object> getQuotes(List<String> symbols, String country) {
        String c = "US".equalsIgnoreCase(country) ? "US" : "KR";
        List<Map<String, Object>> matched = symbols.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .map(sym -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("symbol", sym);
                    m.put("name", null);
                    return m;
                })
                .collect(Collectors.toList());
        Map<String, Object> result = new HashMap<>();
        result.put("results", enrichQuotes(matched, c));
        return result;
    }

    /* ===================== 종목별 AI 요약 (온디맨드) ===================== */

    /**
     * 사용자가 "AI 분석" 버튼을 눌렀을 때만 생성한다(랭킹 30건마다 자동 생성하면 Gemini 호출량이 과도해짐).
     * 심볼당 12시간 캐시(사용자가 새로고침해도 다시 안 사라지도록 프론트 localStorage와 동일 주기로 맞춤).
     * tag=목록에 표시할 한 줄, industry=업종 추정(토스 API엔 없어 Gemini가 함께 추정), summary=팝업 2~3문장.
     */
    public Map<String, Object> getStockInsight(String symbol, String country) {
        String key = "insight:" + symbol;
        return cached(key, 12 * 3600_000, () -> loadStockInsight(symbol, country));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadStockInsight(String symbol, String country) {
        Map<String, Object> fallback = new HashMap<>();
        fallback.put("tag", null);
        fallback.put("industry", null);
        fallback.put("summary", "AI 요약을 생성하지 못했습니다.");
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            fallback.put("summary", "GEMINI_API_KEY가 설정되지 않아 AI 요약을 생성할 수 없습니다.");
            return fallback;
        }
        try {
            // 최근 랭킹 캐시에서 해당 종목의 현재 시세 컨텍스트를 찾아 근거 데이터로 사용
            Map<String, Object> found = null;
            for (String tab : List.of("amount", "gainers", "losers")) {
                Map<String, Object> r = getRankings(country, tab);
                for (Map<String, Object> item : (List<Map<String, Object>>) r.get("rankings")) {
                    if (symbol.equals(item.get("symbol"))) { found = item; break; }
                }
                if (found != null) break;
            }
            String dataJson = found != null ? mapper.writeValueAsString(found) : "{}";

            String prompt = "당신은 증권사 애널리스트입니다. 아래 종목의 실시간 데이터(JSON)를 참고해 한국어로 분석하세요.\n"
                    + "반드시 순수 JSON만 출력하세요(마크다운 백틱 금지). 스키마:\n"
                    + "{\"tag\": \"목록에 표시할 8자 내외 짧은 키워드(예: 반도체 강세, 실적 호조, 매수의견 개시)\",\n"
                    + " \"industry\": \"이 종목이 속한 업종/섹터를 한 단어~짧은 구로 (예: 반도체, 2차전지, 바이오, 인터넷·플랫폼). 확신 없으면 알고 있는 선에서 가장 근접한 분류\",\n"
                    + " \"summary\": \"2~3문장 요약. 등락 배경이나 소속 산업 특징 위주로, 확인되지 않은 사실은 단정하지 말 것\"}\n"
                    + "투자 권유가 아닌 데이터 기반 관찰만 서술하세요.\n\n종목: " + symbol + "\n데이터: " + dataJson;

            String text = callGemini(prompt);
            if (text == null) return fallback;
            String cleaned = text.replaceAll("```json|```", "").trim();
            Map<String, Object> parsed = mapper.readValue(cleaned, Map.class);
            parsed.put("generatedAt", System.currentTimeMillis());
            return parsed;
        } catch (Exception e) {
            log.warn("종목 AI 요약 생성 실패({}): {}", symbol, e.getMessage());
            return fallback;
        }
    }

    /* ===================== AI 시황 브리핑 (Gemini) ===================== */

    private static final String[] GEMINI_MODELS = {
            "gemini-flash-latest", "gemini-3-flash", "gemini-3-flash-preview", "gemini-2.0-flash", "gemini-2.5-flash"
    };

    /**
     * 성공하면 12시간 캐시, 실패하면 10분 뒤 재시도(짧게 재시도하되 실패할 때마다 계속 두드리지는 않음).
     * 이전엔 성공/실패 구분 없이 30분 캐시라 하루 최대 48번 Gemini를 호출했는데, 무료 티어 일일 한도를
     * 다른 기능(종목별 AI 요약, AI 뉴스 브리핑)과 나눠 쓰다 보니 자주 소진되어 계속 실패 문구만 보이는
     * 원인이 됐다. 호출 자체를 하루 2~3회 수준으로 줄여 한도 소진을 피한다.
     */
    public Map<String, Object> getAiBriefing() {
        CacheEntry e = cache.get("briefing");
        long now = System.currentTimeMillis();
        if (e != null && now < e.expiresAt()) {
            @SuppressWarnings("unchecked")
            Map<String, Object> cachedVal = (Map<String, Object>) e.value();
            return cachedVal;
        }
        Map<String, Object> result = loadAiBriefing();
        boolean failed = isBriefingFailure(result);
        long ttl = failed ? 10 * 60_000 : 12 * 3600_000;
        cache.put("briefing", new CacheEntry(now + ttl, result));
        return result;
    }

    private boolean isBriefingFailure(Map<String, Object> result) {
        Object summary = result == null ? null : result.get("summary");
        return summary == null || summary.toString().contains("생성하지 못했") || summary.toString().contains("설정되지 않아");
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
            Map<String, Object> ind = getIndicators();
            context.put("indicators", ind == null ? null : ind.get("items"));
            context.put("investorTrading", getInvestorTrading());
            Map<String, Object> krRank = getRankings("KR", "amount");
            Map<String, Object> usRank = getRankings("US", "amount");
            context.put("krTopAmount", krRank == null ? null : krRank.get("rankings"));
            context.put("usTopAmount", usRank == null ? null : usRank.get("rankings"));
            String contextJson = mapper.writeValueAsString(context);
            if (contextJson.length() > 14000) contextJson = contextJson.substring(0, 14000);

            String prompt = "당신은 증권사 리서치센터의 애널리스트입니다. 아래 실시간 시장 데이터(JSON)를 바탕으로 한국어로 시황을 요약하세요.\n"
                    + "반드시 순수 JSON만 출력하세요(마크다운 백틱 금지). 스키마:\n"
                    + "{\"summary\": \"오늘 시황 요약 3~5문장\",\n"
                    + " \"weekAhead\": [\"이번 주 주목할 일정 3~5개\"],\n"
                    + " \"picks\": [{\"name\": \"종목명\", \"symbol\": \"심볼\", \"market\": \"KR또는US\", \"reason\": \"이유 1~2문장\"}] (3~5개, 반드시 제공된 거래대금 상위 목록 안에서만)}\n"
                    + "투자 권유가 아닌 데이터 기반 관찰만 서술하세요.\n\n데이터:\n" + contextJson;

            String text = callGemini(prompt);
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

    /** 사용 가능한 모델을 찾을 때까지 후보를 순회(404면 다음 모델). 성공한 모델은 기억해 재사용. */
    @SuppressWarnings("unchecked")
    private String callGemini(String prompt) {
        List<String> models = new ArrayList<>();
        if (workingGeminiModel != null) models.add(workingGeminiModel);
        for (String m : GEMINI_MODELS) if (!models.contains(m)) models.add(m);
        for (String model : models) {
            try {
                Map<String, Object> reqBody = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + geminiApiKey;
                ResponseEntity<String> res = rest.postForEntity(url, new HttpEntity<>(mapper.writeValueAsString(reqBody), headers), String.class);
                Map<String, Object> body = mapper.readValue(res.getBody(), Map.class);
                List<Map<String, Object>> candidates = (List<Map<String, Object>>) (Object) listOf(body, "candidates");
                if (candidates.isEmpty()) continue;
                Map<String, Object> content = pickMap(candidates.get(0), "content");
                List<Map<String, Object>> parts = content == null ? List.of() : (List<Map<String, Object>>) (Object) content.getOrDefault("parts", List.of());
                if (parts.isEmpty()) continue;
                String text = pickStr(parts.get(0), "text");
                if (text != null) {
                    workingGeminiModel = model;
                    return text;
                }
            } catch (org.springframework.web.client.HttpClientErrorException.NotFound e) {
                log.warn("Gemini 모델 {} 사용 불가(404) — 다음 후보 시도", model);
            } catch (Exception e) {
                log.warn("Gemini 호출 실패(모델 {}): {}", model, e.getMessage());
                return null;
            }
        }
        return null;
    }
}
