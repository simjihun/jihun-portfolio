package com.jihun.portfolio.news.controller;

import com.jihun.portfolio.news.domain.NewsBriefing;
import com.jihun.portfolio.news.domain.NewsArticle;
import com.jihun.portfolio.news.domain.NewsCategory;
import com.jihun.portfolio.news.repository.NewsBriefingRepository;
import com.jihun.portfolio.news.repository.NewsRepository;
import com.jihun.portfolio.news.service.NewsFetchService;
import com.jihun.portfolio.news.service.NewsPreviewService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RestController
@RequestMapping("/api/news")
public class NewsController {

    private static final int HIGHLIGHT_TARGET = 10;

    private final NewsRepository newsRepository;
    private final NewsBriefingRepository briefingRepository;
    private final NewsFetchService fetchService;
    private final NewsPreviewService previewService;

    public NewsController(NewsRepository newsRepository,
                          NewsBriefingRepository briefingRepository,
                          NewsFetchService fetchService,
                          NewsPreviewService previewService) {
        this.newsRepository = newsRepository;
        this.briefingRepository = briefingRepository;
        this.fetchService = fetchService;
        this.previewService = previewService;
    }

    @GetMapping("/categories")
    public List<Map<String, String>> categories() {
        return Arrays.stream(NewsCategory.values())
                .map(c -> Map.of("code", c.name(), "label", c.getLabel()))
                .toList();
    }

    @GetMapping("/articles")
    public List<NewsArticle> articles(@RequestParam NewsCategory category) {
        return newsRepository.findTop30ByCategoryOrderByPublishedAtDesc(category);
    }

    /**
     * 주요 뉴스 하이라이트. 전체 최신순으로만 뿑으면 특정 카테고리가 배제되므로,
     * 카테고리당 최신 1건을 먼저 보장한 뒤 전체 최신순으로 나머지를 채운다.
     * 각 기사에는 OpenGraph 대표 이미지를 병렬로 붙여 반환한다(이미지는 수집 후 캐시되어 재요청 비용이 없다).
     */
    @GetMapping("/highlights")
    public Map<String, Object> highlights() {
        List<NewsArticle> balanced = new ArrayList<>();
        Set<Long> used = new HashSet<>();
        for (NewsCategory cat : NewsCategory.values()) {
            List<NewsArticle> top = newsRepository.findTop30ByCategoryOrderByPublishedAtDesc(cat);
            if (!top.isEmpty()) {
                NewsArticle a = top.get(0);
                balanced.add(a);
                used.add(a.getId());
            }
        }
        for (NewsArticle a : newsRepository.findTop20ByOrderByPublishedAtDesc()) {
            if (balanced.size() >= HIGHLIGHT_TARGET) break;
            if (used.add(a.getId())) balanced.add(a);
        }
        balanced.sort(Comparator.comparing(NewsArticle::getPublishedAt).reversed());

        // 이미지는 외부 페이지 링크 미리보기를 병렬로 가져온다 (캐시 되어있으면 즉시 반환)
        List<CompletableFuture<Map<String, Object>>> futures = balanced.stream()
                .map(a -> CompletableFuture.supplyAsync(() -> toHighlightMap(a)))
                .toList();
        List<Map<String, Object>> latestWithImages = futures.stream().map(CompletableFuture::join).toList();

        Map<String, Object> res = new HashMap<>();
        res.put("breaking", newsRepository.findTop10ByBreakingTrueAndPublishedAtAfterOrderByPublishedAtDesc(
                LocalDateTime.now().minusHours(24)));
        res.put("latest", latestWithImages);
        res.put("lastFetchAt", fetchService.getLastFetchAt());
        res.put("total", newsRepository.count());
        return res;
    }

    private Map<String, Object> toHighlightMap(NewsArticle a) {
        Map<String, Object> m = new HashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("categoryLabel", a.getCategoryLabel());
        m.put("press", a.getPress());
        m.put("publishedAt", a.getPublishedAt());
        m.put("breaking", a.isBreaking());
        NewsPreviewService.Preview p = previewService.getPreview(a.getId());
        m.put("image", (p != null && p.image() != null && !p.image().isBlank()) ? p.image() : null);
        return m;
    }

    /** 카테고리별 AI 3줄 브리핑 전체 */
    @GetMapping("/briefings")
    public List<NewsBriefing> briefings() {
        return briefingRepository.findAll();
    }

    /** 기사 링크 미리보기 (OpenGraph 메타데이터) */
    @GetMapping("/preview")
    public ResponseEntity<NewsPreviewService.Preview> preview(@RequestParam Long id) {
        NewsPreviewService.Preview p = previewService.getPreview(id);
        return p == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(p);
    }
}
