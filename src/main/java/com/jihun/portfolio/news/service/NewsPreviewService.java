package com.jihun.portfolio.news.service;

import com.jihun.portfolio.news.domain.NewsArticle;
import com.jihun.portfolio.news.repository.NewsRepository;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 기사 링크 미리보기 서비스.
 *
 * 기사 본문을 긁지 않고, 기사 페이지가 공개하는 OpenGraph 메타데이터
 * (대표 이미지, 제목, 1~2줄 소개)만 가져온다.
 * 카카오톡/슬랙의 "링크 미리보기"와 동일한 방식 — 저작권 안전 범위.
 *
 * - 외부 임의 URL이 아니라 우리가 수집한 기사 id로만 조회 (SSRF 방지)
 * - 한 번 가져온 미리보기는 메모리에 캐시해 재요청 시 외부 호출 없음
 */
@Service
public class NewsPreviewService {

    private static final Logger log = LoggerFactory.getLogger(NewsPreviewService.class);
    private static final int CACHE_MAX = 500;

    /** 미리보기 응답 데이터 */
    public record Preview(Long id, String title, String description, String image,
                          String link, String domain, String press, String publishedAt) {}

    private final NewsRepository newsRepository;
    private final Map<Long, Preview> cache = new ConcurrentHashMap<>();

    public NewsPreviewService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    public Preview getPreview(Long articleId) {
        // 1. 캐시 확인
        Preview cached = cache.get(articleId);
        if (cached != null) return cached;

        // 2. 수집해둔 기사인지 확인 (외부 임의 URL 접근 차단)
        NewsArticle article = newsRepository.findById(articleId).orElse(null);
        if (article == null) return null;

        String ogTitle = article.getTitle();
        String ogDesc = "";
        String ogImage = "";
        try {
            Document doc = Jsoup.connect(article.getLink())
                    .userAgent("Mozilla/5.0 (hunit.kr link preview)")
                    .timeout(7000)
                    .get();
            ogTitle = meta(doc, "og:title", ogTitle);
            ogDesc  = meta(doc, "og:description", "");
            ogImage = meta(doc, "og:image", "");
        } catch (Exception e) {
            log.warn("[news] 미리보기 수집 실패 id={}: {}", articleId, e.getMessage());
        }

        String domain;
        try {
            domain = URI.create(article.getLink()).getHost();
        } catch (Exception e) {
            domain = "";
        }

        Preview preview = new Preview(
                article.getId(), ogTitle, ogDesc, ogImage,
                article.getLink(), domain, article.getPress(),
                article.getPublishedAt().toString()
        );

        if (cache.size() >= CACHE_MAX) cache.clear();
        cache.put(articleId, preview);
        return preview;
    }

    private String meta(Document doc, String property, String fallback) {
        String v = doc.select("meta[property=" + property + "]").attr("content");
        return v == null || v.isBlank() ? fallback : v.strip();
    }
}
