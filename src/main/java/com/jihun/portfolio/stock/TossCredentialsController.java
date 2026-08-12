package com.jihun.portfolio.stock;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자 설정 화면에서 토스증권 API 키를 등록/변경하는 API. SecurityConfig의 "/api/admin/**" 규칙으로
 * ROLE_ADMIN만 접근 가능. 값 자체는 절대 응답에 포함하지 않는다(설정 여부만 boolean으로 반환).
 */
@RestController
@RequestMapping("/api/admin/toss-credentials")
public class TossCredentialsController {

    private final TossCredentialsService credentialsService;
    private final TossApiClient tossApiClient;

    public TossCredentialsController(TossCredentialsService credentialsService, TossApiClient tossApiClient) {
        this.credentialsService = credentialsService;
        this.tossApiClient = tossApiClient;
    }

    public record SaveRequest(String clientId, String clientSecret) {}

    @GetMapping
    public Map<String, Object> status() {
        return Map.of("configured", credentialsService.isConfigured());
    }

    @PostMapping
    public Map<String, Object> save(@RequestBody SaveRequest req) {
        credentialsService.save(req.clientId(), req.clientSecret());
        tossApiClient.invalidateToken(); // 새 키로 즉시 다시 로그인하도록 기존 토큰 폐기
        return Map.of("success", true);
    }
}
