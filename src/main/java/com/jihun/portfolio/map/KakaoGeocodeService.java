package com.jihun.portfolio.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 카카오 키워드 검색을 이용한 주소/건물명 → 좌표 변환 서비스.
 *
 * 네이버 Geocoding API는 정식 도로명·지번 주소를 파싱하는 용도라 "구 동 아파트명" 같은
 * 자연어(건물명) 검색에는 빈 결과를 돌려주는 경우가 많다. 카카오 키워드 검색(맛집 검색에도 사용 중)은
 * POI(장소) 검색이라 아파트 단지명도 잘 찾아, 부동산 지도 표시용으로 더 적합하다.
 * KAKAO_API_KEY를 그대로 재사용하므로 별도 키 발급이 필요 없다.
 */
@Service
public class KakaoGeocodeService {

    private static final Logger log = LoggerFactory.getLogger(KakaoGeocodeService.class);
    private static final String ENDPOINT = "https://dapi.kakao.com/v2/local/search/keyword.json";
    private static final double[] NOT_FOUND = new double[0];

    @Value("${KAKAO_API_KEY:}")
    private String apiKey;

    private final RestClient http = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    /** 검색어(지역+동+건물명)를 [위도, 경도]로 변환. 실패하거나 키 미설정 시 null. */
    public double[] geocode(String query) {
        if (apiKey == null || apiKey.isBlank()) {
            return null;
        }
        double[] cached = cache.get(query);
        if (cached != null) {
            return cached.length == 2 ? cached : null;
        }
        try {
            String url = ENDPOINT + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8) + "&size=1";
            String body = http.get().uri(URI.create(url))
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + apiKey)
                    .retrieve().body(String.class);
            JsonNode documents = objectMapper.readTree(body).path("documents");
            if (documents.isArray() && documents.size() > 0) {
                double lat = documents.get(0).path("y").asDouble();
                double lng = documents.get(0).path("x").asDouble();
                double[] result = {lat, lng};
                cache.put(query, result);
                return result;
            }
        } catch (Exception e) {
            log.warn("[geocode] 실패 query={}: {}", query, e.getMessage());
        }
        cache.put(query, NOT_FOUND);
        return null;
    }
}
