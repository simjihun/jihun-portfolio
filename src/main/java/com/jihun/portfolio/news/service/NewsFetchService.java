package com.jihun.portfolio.news.service;

import com.jihun.portfolio.news.domain.NewsArticle;
import com.jihun.portfolio.news.domain.NewsCategory;
import com.jihun.portfolio.news.repository.NewsRepository;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URLConnection;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RSS 뉴스 수집 서비스 (스케줄러 데몬).
 *
 * 30분마다 카테고리별 RSS를 순회하며 새 기사를 수집한다.
 * - 링크 기준 중복 제거
 * - 제목에 [속보]/[단독]가 포함되면 속보로 표시
 * - 3일 지난 기사는 자동 삭제 (테이블 관리)
 * - 피드 하나가 장애나도 나머지 카테고리는 계속 수집 (장애 격리)
 */
@Service
public class NewsFetchService {

    private static final Logger log = LoggerFactory.getLogger(NewsFetchService.class);
    private static final int RETENTION_DAYS = 3;

    /** 카테고리별 RSS 소스 (한국경제 공식 RSS + 주식은 매일경제 증권 RSS) */
    private record FeedSource(String url, String press) {}

    private static final Map<NewsCategory, FeedSource> FEEDS = new LinkedHashMap<>() {{
        put(NewsCategory.POLITICS,      new FeedSource("https://www.hankyung.com/feed/politics", "한국경제"));
        put(NewsCategory.ECONOMY,       new FeedSource("https://www.hankyung.com/feed/economy", "한국경제"));
        put(NewsCategory.SOCIETY,       new FeedSource("https://www.hankyung.com/feed/society", "한국경제"));
        put(NewsCategory.LIFE,          new FeedSource("https://www.hankyung.com/feed/life", "한국경제"));
        put(NewsCategory.WORLD,         new FeedSource("https://www.hankyung.com/feed/international", "한국경제"));
        put(NewsCategory.ENTERTAINMENT, new FeedSource("https://www.hankyung.com/feed/entertainment", "한국경제"));
        put(NewsCategory.STOCK,         new FeedSource("https://www.mk.co.kr/rss/50200011/", "매일경제"));
    }};

    private final NewsRepository newsRepository;

    private volatile LocalDateTime lastFetchAt;

    public NewsFetchService(NewsRepository newsRepository) {
        this.newsRepository = newsRepository;
    }

    /** 앱 시작 10초 후 첫 수집, 이후 30분 간격 */
    @Scheduled(initialDelay = 10_000, fixedDelay = 30 * 60 * 1000)
    @Transactional
    public void fetchAll() {
        int totalNew = 0;
        for (Map.Entry<NewsCategory, FeedSource> entry : FEEDS.entrySet()) {
            try {
                totalNew += fetchFeed(entry.getKey(), entry.getValue());
            } catch (Exception e) {
                // 피드 하나 장애가 전체 수집을 막지 않도록 카테고리 단위로 격리
                log.warn("[news] {} 수집 실패: {}", entry.getKey(), e.getMessage());
            }
        }
        // 보존 기간 지난 기사 정리
        long deleted = newsRepository.deleteByPublishedAtBefore(LocalDateTime.now().minusDays(RETENTION_DAYS));
        lastFetchAt = LocalDateTime.now();
        log.info("[news] 수집 완료: 신규 {}건, 정리 {}건", totalNew, deleted);
    }

    /** 피드 1개 수집 */
    private int fetchFeed(NewsCategory category, FeedSource source) throws Exception {
        URLConnection conn = URI.create(source.url()).toURL().openConnection();
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (hunit.kr news reader)");

        SyndFeed feed = new SyndFeedInput().build(new XmlReader(conn));

        int added = 0;
        for (SyndEntry e : feed.getEntries()) {
            String link = trim(e.getLink(), 600);
            String title = trim(e.getTitle(), 300);
            if (link == null || title == null || title.isBlank()) continue;
            if (newsRepository.existsByLink(link)) continue;   // 중복 제거

            LocalDateTime publishedAt = e.getPublishedDate() != null
                    ? LocalDateTime.ofInstant(e.getPublishedDate().toInstant(), ZoneId.of("Asia/Seoul"))
                    : LocalDateTime.now();
            boolean breaking = title.contains("[속보]") || title.contains("[단독]");

            newsRepository.save(new NewsArticle(category, title, link, source.press(), publishedAt, breaking));
            added++;
        }
        if (added > 0) {
            log.info("[news] {} 신규 {}건", category, added);
        }
        return added;
    }

    private String trim(String s, int max) {
        if (s == null) return null;
        s = s.strip();
        return s.length() > max ? s.substring(0, max) : s;
    }

    public LocalDateTime getLastFetchAt() {
        return lastFetchAt;
    }
}
