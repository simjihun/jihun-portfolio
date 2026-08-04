package com.jihun.portfolio.map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 네이버 지오코딩(주소 → 좌표 변환) 서비스.
 *
 * 부동산 실거래 목록의 "지역+동+아파트명" 문자열을 좌표로 바꿔 지도에 개별 표시하는 데 사용한다.
 * 동일 쿼리는 메모리에 캐시해 반복 호출을 줄인다.
 */
@Service
public class NaverGeocodeService {

    private static final Logger log = LoggerFactory.getLogger(NaverGeocodeService.class);
    private static final String ENDPOINT = "https://naveropenapi.apigw.ntruss.com/map-geocode/v2/geocode";
    private static final double[] NOT_FOUND = new double[0];

    @Value("${NAVER_MAP_CLIENT_ID:}")
    private String clientId;

    @Value("${NAVER_MAP_CLIENT_SECRET:}")
    private String clientSecret;

    private final RestClient http = RestClient.create();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, double[]> cache = new ConcurrentHashMap<>();

    /** 주소 문자열을 [위도, 경도]로 변환. 실패하거나 키 미설정 시 null. */
    public double[] geocode(String query) {
        if (clientId == null || clientId.isBlank() || clientSecret == null || clientSecret.isBlank()) {
            return null;
        }
        double[] cached = cache.get(query);
        if (cached != null) {
            return cached.length == 2 ? cached : null;
        }
        try {
            String url = ENDPOINT + "?query=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
            String body = http.get().uri(URI.create(url))
                    .header("X-NCP-APIGW-API-KEY-ID", clientId)
                    .header("X-NCP-APIGW-API-KEY", clientSecret)
                    .retrieve().body(String.class);
            JsonNode addresses = objectMapper.readTree(body).path("addresses");
            if (addresses.isArray() && addresses.size() > 0) {
                double lat = addresses.get(0).path("y").asDouble();
                double lng = addresses.get(0).path("x").asDouble();
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
