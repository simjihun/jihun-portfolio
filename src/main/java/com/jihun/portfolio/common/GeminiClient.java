package com.jihun.portfolio.common;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Gemini generateContent 공용 호출 클라이언트.
 *
 * AI 뉴스 브리핑(NewsBriefingService)과 AI 주식 브리핑(StockDashboardService)이 각자 모델
 * 후보 목록과 재시도 로직을 따로 구현하고 있었는데, 그 결과 서로 다른 방식으로 실패했다:
 *  - 뉴스 쪽은 모델을 "gemini-2.5-flash" 하나만 하드코딩해서, 그 모델이 API 버전 개편으로
 *    없어지면(404) 카테고리 8개가 전부 실패했다.
 *  - 주식 쪽은 후보 목록이 있었지만, 첫 모델이 503(일시 과부하)이면 다음 후보로 안 넘어가고
 *    그 자리에서 바로 포기했다(404만 "다음 모델 시도"로 처리하고 있었음).
 * 이 클래스로 통합해 두 서비스가 같은 후보 목록·재시도 정책을 공유한다 — 404(모델 없음)든
 * 5xx(일시 과부하)든 다음 후보 모델로 넘어가고, 한 번 성공한 모델은 기억해뒀다가 다음
 * 호출부터 가장 먼저 시도한다.
 */
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    /** 우선순위 순. gemini-flash-latest는 별칭이라 모델 개편에도 가장 잘 버틴다. */
    private static final String[] CANDIDATE_MODELS = {
            "gemini-flash-latest", "gemini-3-flash", "gemini-3-flash-preview", "gemini-2.0-flash", "gemini-2.5-flash"
    };

    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();
    private volatile String workingModel;

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    public GeminiClient() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(4000);
        f.setReadTimeout(30000);
        this.rest = new RestTemplate(f);
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * prompt를 보내 텍스트 응답을 받는다. 후보 모델을 순서대로 시도하고, 모델이 없거나(404)
     * 일시 과부하(5xx)면 다음 후보로 넘어간다. 그 외 예상 못 한 오류는 모델을 바꿔도 똑같이
     * 실패할 가능성이 높아 그 자리에서 포기한다. 전부 실패하면 null.
     */
    public String generate(String prompt) {
        if (!isConfigured()) return null;
        List<String> models = new ArrayList<>();
        if (workingModel != null) models.add(workingModel);
        for (String m : CANDIDATE_MODELS) if (!models.contains(m)) models.add(m);

        for (String model : models) {
            try {
                Map<String, Object> reqBody = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);
                String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + apiKey;
                ResponseEntity<String> res = rest.postForEntity(url, new HttpEntity<>(mapper.writeValueAsString(reqBody), headers), String.class);
                JsonNode root = mapper.readTree(res.getBody());
                JsonNode textNode = root.path("candidates").path(0).path("content").path("parts").path(0).path("text");
                if (textNode.isMissingNode() || textNode.isNull()) continue;
                String text = textNode.asText(null);
                if (text != null && !text.isBlank()) {
                    workingModel = model;
                    return text;
                }
            } catch (HttpClientErrorException.NotFound e) {
                log.warn("[gemini] 모델 {} 사용 불가(404) — 다음 후보 시도", model);
            } catch (HttpServerErrorException e) {
                log.warn("[gemini] 모델 {} 일시 오류({}) — 다음 후보 시도: {}", model, e.getStatusCode(), e.getMessage());
            } catch (Exception e) {
                log.warn("[gemini] 모델 {} 호출 실패: {}", model, e.getMessage());
                return null;
            }
        }
        return null;
    }
}
