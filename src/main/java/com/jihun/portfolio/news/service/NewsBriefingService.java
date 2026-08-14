package com.jihun.portfolio.news.service;

import com.jihun.portfolio.common.GeminiClient;
import com.jihun.portfolio.news.domain.NewsBriefing;
import com.jihun.portfolio.news.domain.NewsCategory;
import com.jihun.portfolio.news.repository.NewsBriefingRepository;
import com.jihun.portfolio.news.repository.NewsRepository;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Gemini 기반 카테고리별 3줄 브리핑 생성 서비스.
 *
 * 스케줄: 하루 3회(09:00 / 15:00 / 21:00, KST) — AI 주식의 시황 브리핑(StockDashboardService)과
 * 동일한 스케줄을 쓴다. Gemini API를 쓰는 정기 생성 작업은 전부 이 세 시각으로 통일해, DB에
 * 저장해두고 방문자는 그 값을 그대로 읽기만 하는 동일한 정책을 따른다(자세한 배경은 CLAUDE.md의
 * 'Gemini API 호출 정책' 참고).
 * - Flash 무료 RPD 250, 카테고리 8개 x 3회 = 하루 24요청으로 여유 있게 운영
 * - 카테고리 간 호출 간격 10초 (분당 요청 제한 방지)
 * - 429 시 재시도 없음 (이번 주기가 실패하면 다음 스케줄 시각에 다시 시도)
 *
 * Gemini 호출 자체(모델 후보 폴백, 404/5xx 처리)는 GeminiClient(common 패키지)가 담당한다 —
 * 예전엔 이 서비스가 "gemini-2.5-flash" 모델 하나만 하드코딩해서, 그 모델이 API 버전 개편으로
 * 없어지자(404) 카테고리 8개가 전부 실패했었다.
 */
@Service
public class NewsBriefingService {

    private static final Logger log = LoggerFactory.getLogger(NewsBriefingService.class);
    private static final int CALL_INTERVAL_MS = 10_000;

    private final NewsRepository newsRepository;
    private final NewsBriefingRepository briefingRepository;
    private final GeminiClient geminiClient;

    public NewsBriefingService(NewsRepository newsRepository,
                               NewsBriefingRepository briefingRepository,
                               GeminiClient geminiClient) {
        this.newsRepository = newsRepository;
        this.briefingRepository = briefingRepository;
        this.geminiClient = geminiClient;
    }

    /** 하루 3회(09:00 / 15:00 / 21:00, KST) 실행 — AI 주식 브리핑과 동일한 스케줄. */
    @Scheduled(cron = "0 0 9,15,21 * * *", zone = "Asia/Seoul")
    public void generateAll() {
        if (!geminiClient.isConfigured()) {
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

    /**
     * 서버가 막 배포되어 DB에 브리핑이 하나도 없을 때만(최초 1회) 다음 스케줄 시각까지 기다리지 않고
     * 채워둔다. NewsFetchService의 최초 수집(앱 시작 10초 후)이 끝날 시간을 준 뒤(60초 대기)
     * 실행한다 — 기사가 아직 하나도 없는 상태에서 부르면 카테고리마다 "기사 부족"으로 전부
     * 스킵되기 때문. 별도 데몬 스레드로 실행해 앱 기동 자체를 지연시키지 않는다.
     */
    @PostConstruct
    public void generateOnStartupIfEmpty() {
        if (briefingRepository.count() > 0) return;
        Thread t = new Thread(() -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            generateAll();
        }, "news-briefing-init");
        t.setDaemon(true);
        t.start();
    }

    private boolean generate(NewsCategory category) {
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

        String content = geminiClient.generate(prompt);
        if (content == null || content.isBlank()) return false;
        briefingRepository.save(new NewsBriefing(category, content.strip()));
        log.info("[brief] {} 브리핑 저장 완료", category);
        return true;
    }
}
