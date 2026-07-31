package com.jihun.portfolio.news.controller;

import com.jihun.portfolio.news.domain.NewsArticle;
import com.jihun.portfolio.news.domain.NewsCategory;
import com.jihun.portfolio.news.repository.NewsRepository;
import com.jihun.portfolio.news.service.NewsFetchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 뉴스 보드 API.
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

    private final NewsRepository newsRepository;
    private final NewsFetchService fetchService;

    public NewsController(NewsRepository newsRepository, NewsFetchService fetchService) {
        this.newsRepository = newsRepository;
        this.fetchService = fetchService;
    }

    /** 카테고리 목록 (탭 구성용: 코드 + 한글 라벨) */
    @GetMapping("/categories")
    public List<Map<String, String>> categories() {
        return Arrays.stream(NewsCategory.values())
                .map(c -> Map.of("code", c.name(), "label", c.getLabel()))
                .toList();
    }

    /** 카테고리별 최신 기사 */
    @GetMapping("/articles")
    public List<NewsArticle> articles(@RequestParam NewsCategory category) {
        return newsRepository.findTop30ByCategoryOrderByPublishedAtDesc(category);
    }

    /** 상단 하이라이트: 최근 24시간 속보 + 전체 최신 주요 뉴스 */
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
}
