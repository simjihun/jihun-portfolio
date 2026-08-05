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
import java.util.List;
import java.util.Map;

/**
 * RSS 뉴스 수집 서비스 (스케줄러 데몬).
 *
 * 30분마다 카테고리별 RSS를 순회하며 새 기사를 수집한다.
 * 카테고리마다 여러 언론사 소스를 등록해 다양한 매체의 기사를 함께 모은다.
 * - 링크 기준 중복 제거
 * - 제목이 완전히 같은 기사는 언론사가 다르더라도 중복으로 보고 거르는다 (먼저 수집된 것 유지)
 * - 제목에 [속보]/[단독]가 포함되면 속보로 표시
 * - 3일 지난 기사는 자동 삭제 (테이블 관리)
 * - 소스 하나가 장애나도 나머지는 계속 수집 (소스 단위 장애 격리)
 */
@Service
public class NewsFetchService {

    private static final Logger log = LoggerFactory.getLogger(NewsFetchService.class);
    private static final int RETENTION_DAYS = 3;

    /** 언론사별 RSS 소스 */
    private record FeedSource(String url, String press) {}

    /**
     * 카테고리별 RSS 소스 목록.
     * SBS는 공식 RSS(news.sbs.co.kr)를 사용하고, 동아일보는 오래전부터 공개된
     * rss.donga.com 주소를 사용한다. 주소가 개편되어 일부 소스가 응답하지 않아도
     * fetchAll()에서 소스 단위로 예외를 잡기 때문에 나머지 수집에는 영향이 없다.
     */
    private static final Map<NewsCategory, List<FeedSource>> FEEDS = new LinkedHashMap<>() {{
        put(NewsCategory.POLITICS, List.of(
                new FeedSource("https://www.hankyung.com/feed/politics", "한국경제"),
                new FeedSource("https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=01&plink=RSSREADER", "SBS"),
                new FeedSource("https://rss.donga.com/politics.xml", "동아일보")
        ));
        put(NewsCategory.ECONOMY, List.of(
                new FeedSource("https://www.hankyung.com/feed/economy", "한국경제"),
                new FeedSource("https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=02&plink=RSSREADER", "SBS"),
                new FeedSource("https://rss.donga.com/economy.xml", "동아일보")
        ));
        put(NewsCategory.SOCIETY, List.of(
                new FeedSource("https://www.hankyung.com/feed/society", "한국경제"),
                new FeedSource("https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=03&plink=RSSREADER", "SBS"),
                new FeedSource("https://rss.donga.com/national.xml", "동아일보")
        ));
        put(NewsCategory.LIFE, List.of(
                new FeedSource("https://www.hankyung.com/feed/life", "한국경제"),
                new FeedSource("https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=08&plink=RSSREADER", "SBS"),
                new FeedSource("https://rss.donga.com/culture.xml", "동아일보")
        ));
        put(NewsCategory.WORLD, List.of(
                new FeedSource("https://www.hankyung.com/feed/international", "한국경제"),
                new FeedSource("https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=07&plink=RSSREADER", "SBS"),
                new FeedSource("https://rss.donga.com/international.xml", "동아일보")
        ));
        put(NewsCategory.ENTERTAINMENT, List.of(
                new FeedSource("https://www.hankyung.com/feed/entertainment", "한국경제"),
                new FeedSource("https://news.sbs.co.kr/news/SectionRssFeed.do?sectionId=14&plink=RSSREADER", "SBS"),
                new FeedSource("https://rss.donga.com/entertainment.xml", "동아일보")
        ));
        put(NewsCategory.STOCK, List.of(
                new FeedSource("https://www.mk.co.kr/rss/50200011/", "매일경제")
        ));
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
        for (Map.Entry<NewsCategory, List<FeedSource>> entry : FEEDS.entrySet()) {
            for (FeedSource source : entry.getValue()) {
                try {
                    totalNew += fetchFeed(entry.getKey(), source);
                } catch (Exception e) {
                    // 소스 하나 장애가 전체 수집을 막지 않도록 소스 단위로 격리
                    log.warn("[news] {} ({}) 수집 실패: {}", entry.getKey(), source.press(), e.getMessage());
                }
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
            if (newsRepository.existsByLink(link)) continue;    // 링크 기준 중복 제거
            if (newsRepository.existsByTitle(title)) continue;  // 언론사만 다른 동일 제목 중복 제거 (먼저 수집된 것 유지)

            LocalDateTime publishedAt = e.getPublishedDate() != null
                    ? LocalDateTime.ofInstant(e.getPublishedDate().toInstant(), ZoneId.of("Asia/Seoul"))
                    : LocalDateTime.now();
            boolean breaking = title.contains("[속보]") || title.contains("[단독]");

            newsRepository.save(new NewsArticle(category, title, link, source.press(), publishedAt, breaking));
            added++;
        }
        if (added > 0) {
            log.info("[news] {} ({}) 신규 {}건", category, source.press(), added);
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
