package com.jihun.portfolio.map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * 지도 기능 공통 API (맛집 검색 + 설정 제공).
 * 카카오 Local API를 서버에서 호출해 응답을 중계한다.
 * REST API 키가 프론트엔드에 노출되지 않도록 서버에서만 관리한다.
 */
@RestController
@RequestMapping("/api/map")
public class MapController {

    private static final Logger log = LoggerFactory.getLogger(MapController.class);
    private static final String KAKAO_LOCAL = "https://dapi.kakao.com/v2/local/search";

    @Value("${KAKAO_API_KEY:}")
    private String kakaoApiKey;

    @Value("${NAVER_MAP_CLIENT_ID:}")
    private String naverMapClientId;

    @Value("${NAVER_MAP_CLIENT_SECRET:}")
    private String naverMapClientSecret;

    @Value("${REALESTATE_API_KEY:}")
    private String realEstateApiKey;

    private final RestClient http = RestClient.create();

    /**
     * 장소 검색 (GET /api/map/search)
     * lat/lng가 함께 오면 해당 좌표 기준으로 가까운 순으로 정렬한다.
     * categoryGroupCode를 주면 카카오 공식 카테고리 그룹코드(FD6=음식점, CE7=카페 등)로 필터링한다.
     *
     * keyword가 비어 있고 categoryGroupCode만 있으면 카카오 "카테고리 검색" API로 전환한다.
     * (예: 편의점·대형마트처럼 이름에 검색어가 잘 안 붙는 카테고리를 고를 때, "맛집" 같은
     * 이전 검색어가 남아있으면 키워드 검색으로는 카테고리와 검색어가 둘 다 맞는 곳이 거의 없어
     * 결과가 1~2개로 쪼그라들거나 아예 안 나온다 — 카테고리 검색 API는 위치·반경만으로 찾으므로
     * 이 문제가 없다. 단, 이 API는 위치(x,y)가 필수다.)
     */
    @GetMapping("/search")
    public ResponseEntity<String> search(
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size,
            @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lng,
            @RequestParam(required = false) String categoryGroupCode) {

        if (kakaoApiKey == null || kakaoApiKey.isBlank()) {
            return ResponseEntity.ok("{\"documents\":[],\"meta\":{\"total_count\":0},\"error\":\"KAKAO_API_KEY 미설정\"}");
        }

        boolean hasKeyword = keyword != null && !keyword.isBlank();
        boolean hasCategory = categoryGroupCode != null && !categoryGroupCode.isBlank();

        try {
            String url;
            if (!hasKeyword && hasCategory) {
                if (lat == null || lng == null) {
                    return ResponseEntity.ok("{\"documents\":[],\"meta\":{\"total_count\":0},\"error\":\"카테고리 검색은 위치 정보가 필요합니다.\"}");
                }
                url = KAKAO_LOCAL + "/category.json?category_group_code=" + categoryGroupCode
                        + "&x=" + lng + "&y=" + lat + "&radius=5000"
                        + "&page=" + page + "&size=" + Math.min(size, 15) + "&sort=distance";
            } else {
                StringBuilder sb = new StringBuilder(KAKAO_LOCAL + "/keyword.json?query=")
                        .append(URLEncoder.encode(hasKeyword ? keyword : "맛집", StandardCharsets.UTF_8))
                        .append("&page=").append(page)
                        .append("&size=").append(Math.min(size, 15));
                if (hasCategory) {
                    url.append("&category_group_code=").append(categoryGroupCode);
                }
                if (lat != null && lng != null) {
                    // 카카오 로컬 API: x=경도, y=위도, radius(m), sort=distance로 현재 위치 기준 정렬
                    url.append("&x=").append(lng).append("&y=").append(lat)
                            .append("&radius=20000&sort=distance");
                }
                url = sb.toString();
            }
            String result = http.get()
                    .uri(URI.create(url))
                    .header(HttpHeaders.AUTHORIZATION, "KakaoAK " + kakaoApiKey)
                    .retrieve()
                    .body(String.class);
            return ResponseEntity.ok(result);
        } catch (Exception e) {
            log.warn("[map] 카카오 검색 실패: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                    .body("{\"documents\":[],\"meta\":{\"total_count\":0}}");
        }
    }

    /** 네이버 지도 Client ID 제공 (읽기 전용 식별자라 프론트엔드 노출되어도 안전) */
    @GetMapping("/config")
    public Map<String, String> config() {
        return Map.of("naverMapClientId", naverMapClientId != null ? naverMapClientId : "");
    }

    /** API 키 설정 상태 확인용 헬스체크 */
    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "kakaoKeySet", kakaoApiKey != null && !kakaoApiKey.isBlank(),
                "naverKeySet", naverMapClientId != null && !naverMapClientId.isBlank(),
                "naverSecretSet", naverMapClientSecret != null && !naverMapClientSecret.isBlank(),
                "realEstateKeySet", realEstateApiKey != null && !realEstateApiKey.isBlank()
        );
    }
}
