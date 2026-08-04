package com.jihun.portfolio.map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.parser.Parser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 국토부 아파트매매 실거래가 상세자료(OpenAPI) 연동.
 *
 * - 응답이 XML이라 jsoup의 XML 파서로 파싱한다.
 * - 서비스키는 이미 인코딩된(Encoding) 키를 그대로 쓰므로 URI.create()로 직접 구성해
 *   Spring이 이중으로 인코딩하지 않도록 한다.
 * - 엔드포인트는 공공데이터포털 신규 통합 도메인(apis.data.go.kr, HTTPS)을 사용한다.
 * - 연결/응답 타임아웃을 명시해, 외부 API가 응답 없이 멈춰도 화면이 무한 대기하지 않게 한다.
 * - 아파트 단지(아파트명+동) 단위로 네이버 지오코딩을 호출해 좌표를 붙여, 지도에 개별 위치로
 *   표시할 수 있게 한다. 같은 단지의 여러 거래는 좌표를 재사용해 호출 횟수를 최소화한다.
 */
@RestController
@RequestMapping("/api/map")
public class RealEstateController {

    private static final Logger log = LoggerFactory.getLogger(RealEstateController.class);
    private static final String ENDPOINT =
            "https://apis.data.go.kr/1613000/RTMSDataSvcAptTradeDev/getRTMSDataSvcAptTradeDev";
    private static final int GEOCODE_BUDGET = 40; // 요청 1회당 지오코딩 최대 호출 수(단지 단위)

    @Value("${REALESTATE_API_KEY:}")
    private String serviceKey;

    private final NaverGeocodeService geocodeService;
    private final RestClient http = buildHttpClient();

    public RealEstateController(NaverGeocodeService geocodeService) {
        this.geocodeService = geocodeService;
    }

    private static RestClient buildHttpClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);
        factory.setReadTimeout(10_000);
        return RestClient.builder().requestFactory(factory).build();
    }

    /** 조회 가능한 지역 목록 (드롭다운 구성용) */
    @GetMapping("/estate/regions")
    public List<Map<String, Object>> regions() {
        return RealEstateRegions.ALL.stream()
                .map(r -> Map.<String, Object>of("code", r.code(), "name", r.name(), "lat", r.lat(), "lng", r.lng()))
                .toList();
    }

    /**
     * 아파트 매매 실거래 조회
     * 예) /api/map/estate?lawdCd=11680&dealYmd=202606&keyword=은마
     */
    @GetMapping("/estate")
    public Map<String, Object> estate(@RequestParam String lawdCd,
                                      @RequestParam String dealYmd,
                                      @RequestParam(required = false) String keyword) {
        if (serviceKey == null || serviceKey.isBlank()) {
            return Map.of("items", List.of(), "count", 0, "error", "REALESTATE_API_KEY 미설정");
        }
        try {
            String url = ENDPOINT + "?serviceKey=" + serviceKey
                    + "&LAWD_CD=" + lawdCd + "&DEAL_YMD=" + dealYmd
                    + "&numOfRows=300&pageNo=1";

            log.info("[estate] 조회 요청 lawdCd={} dealYmd={}", lawdCd, dealYmd);
            String xml = http.get().uri(URI.create(url)).retrieve().body(String.class);
            Document doc = Jsoup.parse(xml, "", Parser.xmlParser());

            String resultCode = text(doc, "resultCode");
            if (resultCode != null && !resultCode.equals("00") && !resultCode.equals("000")) {
                String msg = text(doc, "resultMsg");
                log.warn("[estate] API 응답 오류 code={} msg={}", resultCode, msg);
                return Map.of("items", List.of(), "count", 0, "error", "공공API 응답 오류: " + msg);
            }

            String regionName = RealEstateRegions.ALL.stream()
                    .filter(r -> r.code().equals(lawdCd))
                    .findFirst()
                    .map(RealEstateRegions.Region::name)
                    .orElse("");

            Map<String, double[]> geoCache = new HashMap<>();
            List<Map<String, Object>> items = new ArrayList<>();
            long sum = 0;

            for (Element item : doc.select("item")) {
                String aptNm = text(item, "aptNm");
                if (aptNm == null) continue;
                if (keyword != null && !keyword.isBlank() && !aptNm.contains(keyword.strip())) continue;

                String amountRaw = text(item, "dealAmount");
                long amount = parseAmount(amountRaw);
                sum += amount;

                String year = text(item, "dealYear");
                String month = text(item, "dealMonth");
                String day = text(item, "dealDay");
                String dealDate = year + "-" + pad(month) + "-" + pad(day);
                String dong = nz(text(item, "umdNm"));

                // 같은 단지(동+아파트명)는 좌표를 재사용해 지오코딩 호출을 최소화
                String geoKey = dong + "|" + aptNm;
                double[] coord;
                if (geoCache.containsKey(geoKey)) {
                    coord = geoCache.get(geoKey);
                } else if (geoCache.size() < GEOCODE_BUDGET) {
                    coord = geocodeService.geocode(regionName + " " + dong + " " + aptNm);
                    geoCache.put(geoKey, coord);
                } else {
                    coord = null;
                }

                Map<String, Object> row = new LinkedHashMap<>();
                row.put("aptNm", aptNm);
                row.put("dong", dong);
                row.put("area", nz(text(item, "excluUseAr")));
                row.put("floor", nz(text(item, "floor")));
                row.put("buildYear", nz(text(item, "buildYear")));
                row.put("dealAmount", formatAmount(amount));
                row.put("dealDate", dealDate);
                row.put("lat", coord != null ? coord[0] : null);
                row.put("lng", coord != null ? coord[1] : null);
                items.add(row);
            }

            items.sort(Comparator.comparing(m -> (String) m.get("dealDate"), Comparator.reverseOrder()));

            long avg = items.isEmpty() ? 0 : sum / items.size();
            log.info("[estate] 조회 완료 건수={}", items.size());
            return Map.of(
                    "items", items,
                    "count", items.size(),
                    "avgAmount", formatAmount(avg)
            );
        } catch (Exception e) {
            log.warn("[estate] 조회 실패: {}", e.toString());
            return Map.of("items", List.of(), "count", 0, "error", "조회 중 오류가 발생했습니다");
        }
    }

    private String text(Element root, String tag) {
        Element el = root.selectFirst(tag);
        return el == null ? null : el.text().strip();
    }

    private String nz(String s) {
        return s == null ? "-" : s;
    }

    private String pad(String s) {
        if (s == null) return "01";
        return s.length() == 1 ? "0" + s : s;
    }

    private long parseAmount(String raw) {
        if (raw == null) return 0;
        try {
            return Long.parseLong(raw.replaceAll("[^0-9]", ""));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private String formatAmount(long manwon) {
        long eok = manwon / 10000;
        long rem = manwon % 10000;
        if (eok > 0) {
            return rem > 0 ? eok + "억 " + String.format("%,d", rem) + "만원" : eok + "억원";
        }
        return String.format("%,d", manwon) + "만원";
    }
}
