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
 * 스케줄: 매일 오전 7시 1회 (비용 최소화 및 무료 티어 일일 할당량 안에서 수용)
 * - Flash 무료 RPD 250, 카테고리 7개 x 1회 = 하루 7요청으로 안정적 운영
 * - 카테고리 간 호출 간격 10초 (분당 요청 제한 방지)
 * - 429 시 재시도 없음 (1회 요청이 실패하면 다음날 실행 시 다시 시도)
 */
@Service
public class NewsBriefingService {

    private static final Logger log = LoggerFactory.getLogger(NewsBriefingService.class);
    private static final String MODEL = "gemini-2.5-flash";
    private static final int CALL_INTERVAL_MS = 10_000;

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

    /**
     * 매일 오전 7시 실행 (cron: 초 분 시 일 월 요일)
     * RPD 250 무료 티어에서 7개 카테고리x1회 = 7요청/일로 안정적으로 운영
     */
    @Scheduled(cron = "0 0 7 * * *")
    public void generateAll() {
        if (apiKey == null || apiKey.isBlank()) {
            log.info("[brief] GEMINI_API_KEY 미설정 - AI 브리핑 비활성화");
            return;
        }
        log.info("[brief] AI 브리핑 생성 시작 (카테고리당 {}ms 간격)", CALL_INTERVAL_MS);
        int ok = 0;
        for (NewsCategory category : NewsCategory.values()) {
            try {
                if (generate(category)) ok++;
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

    private boolean generate(NewsCategory category) throws Exception {
        var articles = newsRepository.findTop30ByCategoryOrderByPublishedAtDesc(category);
        if (articles.size() < 3) {
            log.info("[brief] {} 기사 부족 스킵", category);
            return false;
        }

        StringBuilder titles = new StringBuilder();
        for (var a : articles) titles.append("- ").append(a.getTitle()).append("\n");

        String prompt = "다음은 최근 수집된 '" + category.getLabel() + "' 분야 뉴스 헤드라인 목록입니다.\n\n"
                + titles
                + "위 헤드라인들을 종합해 오늘의 " + category.getLabel() + " 뉴스 흐름을 정확히 3줄로 요약해 주세요.\n"
                + "규칙:\n"
                + "- 각 줄은 60자 이내의 완결된 평서문\n"
                + "- 줄 사이는 줄바꿈 문자만 사용 (번호, 불릿, 이모지, 머리말 금지)\n"
                + "- 헤드라인에 없는 내용을 추측하거나 덧붙이지 말 것\n";

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
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Gemini 응답 오류 HTTP " + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        return root.path("candidates").path(0)
                .path("content").path("parts").path(0)
                .path("text").asText(null);
    }
}
