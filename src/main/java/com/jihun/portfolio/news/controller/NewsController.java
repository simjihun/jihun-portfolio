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
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
public class NewsController {

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

    @GetMapping("/highlights")
    public Map<String, Object> highlights() {
        return Map.of(
                "breaking", newsRepository.findTop6ByBreakingTrueAndPublishedAtAfterOrderByPublishedAtDesc(
                        LocalDateTime.now().minusHours(24)),
                "latest", newsRepository.findTop8ByOrderByPublishedAtDesc(),
                "lastFetchAt", fetchService.getLastFetchAt(),
                "total", newsRepository.count()
        );
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
