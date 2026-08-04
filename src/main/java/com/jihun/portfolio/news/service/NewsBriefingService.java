package com.jihun.portfolio.news.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jihun.portfolio.news.domain.NewsBriefing;
import com.jihun.portfolio.news.domain.NewsCategory;
import com.jihun.portfolio.news.repository.NewsBriefingRepository;
import com.jihun.portfolio.news.repository.NewsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Gemini 기반 카테고리별 3줄 브리핑 생성 서비스.
 *
 * - 수집된 기사 "제목"들만 입력으로 사용 (본문 미사용 — 저작권 고려)
 * - GEMINI_API_KEY 환경변수(conf/app.conf)에서 키를 읽는다.
 *   키가 없으면 기능만 조용히 비활성화 (로컬 개발 시 에러 없음)
 * - 8시간마다 카테고리 7종 브리핑 갱신
 * - 카테고리 간 호출 간격 5초 (무료 티어 분당 요청 제한 방지)
 */
@Service
public class NewsBriefingService {

    private static final Logger log = LoggerFactory.getLogger(NewsBriefingService.class);
    private static final String MODEL = "gemini-2.5-flash";
    private static final int CALL_INTERVAL_MS = 5000; // 카테고리 간 5초 대기
    private static final int MAX_RETRY = 2;           // 429 시 재시도 횟수

    private final NewsRepository newsRepository;
    private final NewsBriefingRepository briefingRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Value("${GEMINI_API_KEY:}")
    private String apiKey;

    public NewsBriefingService(NewsRepository newsRepository,
                               NewsBriefingRepository briefingRepository) {
        this.newsRepository = newsRepository;
        this.briefingRepository = briefingRepository;
    }

    /** 시작 90초 뒤 첫 생성, 이후 8시간 간격 */
    @Scheduled(initialDelay = 90_000, fixedDelay = 8 * 60 * 60 * 1000)
    public void generateAll() {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("[brief] GEMINI_API_KEY 미설정 - AI 브리핑 비활성화");
            return;
        }
        log.info("[brief] AI 브리핑 생성 시작 (카테고리당 {}ms 간격)", CALL_INTERVAL_MS);
        int ok = 0;
        for (NewsCategory category : NewsCategory.values()) {
            try {
                if (generateWithRetry(category)) ok++;
                Thread.sleep(CALL_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.warn("[brief] {} 브리핑 생성 실패: {}", category, e.getMessage());
            }
        }
        log.info("[brief] AI 브리핑 갱신 완료 ({}/{}개 카테고리)", ok, NewsCategory.values().length);
    }

    /** 429 시 대기 후 재시도 */
    private boolean generateWithRetry(NewsCategory category) throws Exception {
        for (int attempt = 0; attempt <= MAX_RETRY; attempt++) {
            try {
                return generate(category);
            } catch (IllegalStateException e) {
                if (e.getMessage().contains("429") && attempt < MAX_RETRY) {
                    long wait = 15_000L * (attempt + 1); // 15초, 30초 순차 대기
                    log.warn("[brief] {} 429 에러, {}ms 후 재시도 ({}/{})",
                            category, wait, attempt + 1, MAX_RETRY);
                    Thread.sleep(wait);
                } else {
                    throw e;
                }
            }
        }
        return false;
    }

    private boolean generate(NewsCategory category) throws Exception {
        var articles = newsRepository.findTop30ByCategoryOrderByPublishedAtDesc(category);
        if (articles.size() < 3) {
            log.info("[brief] {} 기사 부족(건) 스킵", category);
            return false;
        }

        StringBuilder titles = new StringBuilder();
        for (var a : articles) titles.append("- ").append(a.getTitle()).append("\n");

        String prompt = """
                다음은 최근 수집된 '%s' 분야 뉴스 헤드라인 목록입니다.

                %s
                위 헤드라인들을 종합해 오늘의 %s 뉴스 흐름을 정확히 3줄로 요약해 주세요.
                규칙:
                - 각 줄은 60자 이내의 완결된 평서문
                - 줄 사이는 줄바꿈 문자만 사용 (번호, 불릿, 이모지, 머리말 금지)
                - 헤드라인에 없는 내용을 추측하거나 덧붙이지 말 것
                """.formatted(category.getLabel(), titles, category.getLabel());

        String content = callGemini(prompt);
        if (content == null || content.isBlank()) return false;
        briefingRepository.save(new NewsBriefing(category, content.strip()));
        log.info("[brief] {} 브리핑 저장 완료", category);
        return true;
    }

    private String callGemini(String prompt) throws Exception {
        Map<String, Object> body = Map.of(
                "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt))))
        );
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://generativelanguage.googleapis.com/v1beta/models/"
                        + MODEL + ":generateContent"))
                .header("Content-Type", "application/json")
                .header("x-goog-api-key", apiKey)
                .timeout(Duration.ofSeconds(30))
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 429) {
            throw new IllegalStateException("429 Too Many Requests - Gemini 할당량 초과");
        }
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gemini 응답 오류 HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return root.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText(null);
    }
}
