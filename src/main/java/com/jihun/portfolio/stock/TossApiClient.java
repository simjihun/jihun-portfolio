package com.jihun.portfolio.stock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 토스증권 Open API 클라이언트.
 * - OAuth2 Client Credentials 토큰을 발급받아 메모리에 캐싱(만료 60초 전 자동 재발급)한다.
 * - 시세·랭킹·환율 등 공개 데이터만 사용하므로 계좌 헤더는 필요 없다.
 * - 주의: 토스 WTS 설정의 '허용 IP'에 EC2 공인 IP가 등록되어 있어야 한다(미등록 시 403).
 */
@Component
public class TossApiClient {

    private static final Logger log = LoggerFactory.getLogger(TossApiClient.class);
    private static final String BASE = "https://openapi.tossinvest.com";

    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${TOSS_CLIENT_ID:}")
    private String clientId;
    @Value("${TOSS_CLIENT_SECRET:}")
    private String clientSecret;

    private volatile String accessToken;
    private volatile long tokenExpiresAt; // epoch millis

    public TossApiClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4000);
        f.setReadTimeout(6000);
        this.rest = new RestTemplate(f);
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    /** 경로(+쿼리스트링)로 GET 호출 후 JSON을 Map으로 반환. 실패 시 null. 401이면 토큰 재발급 후 1회 재시도. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String pathAndQuery) {
        if (!isConfigured()) return null;
        for (int attempt = 0; attempt < 2; attempt++) {
            String token = currentToken(attempt > 0);
            if (token == null) return null;
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(token);
                ResponseEntity<String> res = rest.exchange(BASE + pathAndQuery, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                if (res.getBody() == null) return null;
                return mapper.readValue(res.getBody(), Map.class);
            } catch (org.springframework.web.client.HttpClientErrorException.Unauthorized e) {
                log.warn("토스 API 401 — 토큰 재발급 후 재시도: {}", pathAndQuery);
                // 다음 루프에서 forceRefresh
            } catch (Exception e) {
                log.warn("토스 API 호출 실패 {}: {}", pathAndQuery, e.getMessage());
                return null;
            }
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    private synchronized String currentToken(boolean forceRefresh) {
        long now = System.currentTimeMillis();
        if (!forceRefresh && accessToken != null && now < tokenExpiresAt - 60_000) {
            return accessToken;
        }
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("grant_type", "client_credentials");
            form.add("client_id", clientId);
            form.add("client_secret", clientSecret);
            ResponseEntity<String> res = rest.postForEntity(BASE + "/oauth2/token", new HttpEntity<>(form, headers), String.class);
            Map<String, Object> body = mapper.readValue(res.getBody(), Map.class);
            Object token = body.get("access_token");
            Object expiresIn = body.get("expires_in");
            if (token != null) {
                accessToken = token.toString();
                long ttlSec = expiresIn instanceof Number n ? n.longValue() : 3600;
                tokenExpiresAt = now + ttlSec * 1000;
                return accessToken;
            }
        } catch (Exception e) {
            log.error("토스 OAuth 토큰 발급 실패: {}", e.getMessage());
        }
        return null;
    }
}
