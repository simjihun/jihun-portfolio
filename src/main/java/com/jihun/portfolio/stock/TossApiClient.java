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
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * 토스증권 Open API 클라이언트.
 * - OAuth2 토큰 캐싱(만료 60초 전 재발급), 401 시 재발급 후 재시도.
 * - 레이트리밋 보호장치: 모든 호출을 전역 스로틀(호출 간 최소 300ms)로 직렬화하고,
 *   429 수신 시 Retry-After 헤더만큼(없으면 1.2초) 대기 후 1회 재시도한다.
 *   (토스 문서 권장: MARKET_INFO 그룹은 초당 3회뿐이라 버스트 호출 시 쉽게 429가 난다)
 * - 허용 IP에 EC2 공인 IP가 등록되어 있어야 한다(미등록 시 403).
 * - 계좌/자산/주문 조회(계좌 목록 제외)는 시세 조회와 같은 client_id/secret으로 토큰을 받되,
 *   X-Tossinvest-Account 헤더에 계좌의 accountSeq(GET /api/v1/accounts 응답값)를 추가로 실어야 한다
 *   — get(path, accountSeq) 오버로드로 지원. 매수/매도(주문 생성) 엔드포인트는 이 프로젝트에서
 *   의도적으로 구현하지 않는다(조회 전용, 실제 매매는 토스증권 앱에서 직접).
 */
@Component
public class TossApiClient {

    private static final Logger log = LoggerFactory.getLogger(TossApiClient.class);
    private static final String BASE = "https://openapi.tossinvest.com";
    private static final long MIN_CALL_INTERVAL_MS = 300;

    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Object throttleLock = new Object();
    private long lastCallAt = 0;

    @Value("${TOSS_CLIENT_ID:}")
    private String clientId;
    @Value("${TOSS_CLIENT_SECRET:}")
    private String clientSecret;

    private volatile String accessToken;
    private volatile long tokenExpiresAt;

    public TossApiClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4000);
        f.setReadTimeout(6000);
        this.rest = new RestTemplate(f);
    }

    public boolean isConfigured() {
        return clientId != null && !clientId.isBlank() && clientSecret != null && !clientSecret.isBlank();
    }

    /** 호출 간격을 강제해 초당 그룹 한도를 넘지 않도록 한다. */
    private void throttle() {
        synchronized (throttleLock) {
            long wait = lastCallAt + MIN_CALL_INTERVAL_MS - System.currentTimeMillis();
            if (wait > 0) {
                try { Thread.sleep(wait); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            }
            lastCallAt = System.currentTimeMillis();
        }
    }

    /** 시세 등 계좌 무관 공개 데이터 조회 */
    public Map<String, Object> get(String pathAndQuery) {
        return get(pathAndQuery, null);
    }

    /** GET 호출 후 JSON을 Map으로 반환. accountSeq가 있으면 X-Tossinvest-Account 헤더를 추가한다(계좌/자산/주문 조회용).
     * 실패 시 null. 401→토큰 재발급, 429→Retry-After 대기 후 재시도. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> get(String pathAndQuery, Long accountSeq) {
        if (!isConfigured()) return null;
        boolean forceRefresh = false;
        for (int attempt = 0; attempt < 3; attempt++) {
            String token = currentToken(forceRefresh);
            if (token == null) return null;
            forceRefresh = false;
            throttle();
            try {
                HttpHeaders headers = new HttpHeaders();
                headers.setBearerAuth(token);
                if (accountSeq != null) headers.set("X-Tossinvest-Account", String.valueOf(accountSeq));
                ResponseEntity<String> res = rest.exchange(BASE + pathAndQuery, HttpMethod.GET, new HttpEntity<>(headers), String.class);
                if (res.getBody() == null) return null;
                return mapper.readValue(res.getBody(), Map.class);
            } catch (HttpClientErrorException.Unauthorized e) {
                log.warn("토스 API 401 — 토큰 재발급 후 재시도: {}", pathAndQuery);
                forceRefresh = true;
            } catch (HttpClientErrorException.TooManyRequests e) {
                long waitMs = 1200;
                try {
                    String ra = e.getResponseHeaders() != null ? e.getResponseHeaders().getFirst("Retry-After") : null;
                    if (ra != null) waitMs = Math.min(5000, (long) (Double.parseDouble(ra) * 1000) + 200);
                } catch (Exception ignored) {}
                log.warn("토스 API 429 — {}ms 대기 후 재시도: {}", waitMs, pathAndQuery);
                try { Thread.sleep(waitMs); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); return null; }
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
