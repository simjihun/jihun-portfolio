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

    @Value("${REALESTATE_API_KEY:}")
    private String realEstateApiKey;

    private final RestClient http = RestClient.create();

    /**
     * 장소 키워드 검색 (GET /api/map/search)
     * 예) /api/map/search?keyword=화성 삼격살&page=1
     */
    @GetMapping("/search")
    public ResponseEntity<String> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "15") int size) {

        if (kakaoApiKey == null || kakaoApiKey.isBlank()) {
            return ResponseEntity.ok("{\"documents\":[],\"meta\":{\"total_count\":0},\"error\":\"KAKAO_API_KEY 미설정\"}");
        }
        try {
            String result = http.get()
                    .uri(KAKAO_LOCAL + "/keyword.json?query={q}&page={p}&size={s}",
                            keyword, page, Math.min(size, 15))
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
                "realEstateKeySet", realEstateApiKey != null && !realEstateApiKey.isBlank()
        );
    }
}
