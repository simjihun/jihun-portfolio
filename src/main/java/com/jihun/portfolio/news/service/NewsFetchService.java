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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * RSS 뉴스 수집 서비스 (스케줄러 데몬).
 *
 * 30분마다 카테고리별 RSS를 순회하며 새 기사를 수집한다.
 * 카테고리마다 여러 언론사 소스를 등록해 다양한 매체의 기사를 함께 모은다.
 * - 링크 기준 중복 제거
 * - 제목을 정규화(공백 정리 + 특수 따옴표 통일)한 뒤 완전히 같은 기사는 언론사가 달라도 중복으로 보고 거른다(먼저 수집된 것 유지) — 저비용의 1차 필터
 * - 매 수집 주기마다 보존 기간 내 기사 전체를 문자 2-그램 자카드 유사도로 비교해 근사 중복(표현은 다르지만 같은 사건)까지
 *   추가로 정리한다 — 언론사마다 제목을 다르게 뽑아써서 문자열은 달라도 같은 사건을 다루는 경우가 많기 때문에,
 *   완전일치보다 넓게 잡아야 실제로 걸러진다. 카테고리 경계 없이 전체를 대상으로 한다(같은 사건이 언론사 성격에 따라
 *   다른 카테고리로 분류되는 경우도 있기 때문 — 예: 법원 판결 기사가 사회면과 증권면에 동시에 실리는 경우).
 * - 제목에 [속보]/[단독]가 포함되면 속보로 표시
 * - 3일 지난 기사는 자동 삭제 (테이블 관리)
 * - 소스 하나가 장애나도 나머지는 계속 수집 (소스 단위 장애 격리)
 */
@Service
public class NewsFetchService {

    private static final Logger log = LoggerFactory.getLogger(NewsFetchService.class);
    private static final int RETENTION_DAYS = 3;

    /** 문자 2-그램 자카드 유사도가 이 값 이상이면 같은 사건을 다룬 기사로 보고 하나만 남긴다. */
    private static final double DUPLICATE_SIMILARITY_THRESHOLD = 0.40;

    /** 근사중복 정리는 대상 기사 수의 제곱에 비례해 느려진다(전체를 서로 비교). 정상 운영 중에는
     *  보존 기간(3일)·30분 주기 수집을 감안하면 이 상한을 넘길 일이 거의 없지만, 혹시 어떤 이유로
     *  (예: 삭제 실패, 일시적 폭주) 대상이 비정상적으로 많아지면 이 정리 작업 자체가 오래 걸려
     *  스케줄러 스레드를 오래 붙잡게 된다 — 그 경우 이번 주기는 건너뛰고 다음 주기에 다시 시도해
     *  한 번의 폭주가 다른 스케줄 작업들(AI 브리핑 등)까지 지연시키지 않도록 한다. */
    private static final int MAX_DEDUP_CANDIDATES = 3000;

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
        put(NewsCategory.IT_SCIENCE, List.of(
                new FeedSource("https://www.hankyung.com/feed/it", "한국경제")
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
        // 근사 중복(문구는 다르지만 같은 사건) 정리 — 매 수집 주기마다 보존 기간 전체를 다시 훑는다
        int dupDeleted = cleanupNearDuplicateTitles();
        lastFetchAt = LocalDateTime.now();
        log.info("[news] 수집 완료: 신규 {}건, 보존기간 정리 {}건, 근사중복 정리 {}건", totalNew, deleted, dupDeleted);
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
            String rawTitle = trim(e.getTitle(), 300);
            if (link == null || rawTitle == null || rawTitle.isBlank()) continue;
            String title = normalizeTitle(rawTitle);
            if (newsRepository.existsByLink(link)) continue;    // 링크 기준 중복 제거
            if (newsRepository.existsByTitle(title)) continue;  // 정규화한 제목 완전일치 중복 제거(저비용 1차 필터, 나머지는 수집 후 근사중복 정리가 처리)

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

    /**
     * 제목 정규화 — 스마트 따옴표(‘’“”)를 일반 따옴표('\")로 통일하고 연속 공백을 하나로 줄인 뒤 양끝 공백을 제거한다.
     * 언론사마다 RSS에 실리는 따옴표 문자 코드가 달라, 사람 눈에는 완전히 같은 제목인데도 바이트가
     * 달라 중복 판정을 통과하는 경우가 있었다 — 저장 전에 정규화해 이 문제를 근본적으로 막는다.
     */
    private String normalizeTitle(String title) {
        String s = title
                .replaceAll("[\u2018\u2019\u201B\u2032]", "'")
                .replaceAll("[\u201C\u201D\u201F\u2033]", "\"")
                .replaceAll("\\s+", " ")
                .strip();
        return s.length() > 300 ? s.substring(0, 300) : s;
    }

    /**
     * 유사도 비교용 정규화 — [속보]/[단독] 같은 태그와 따옴표·공백·구두점을 모두 제거해 순수 내용 글자만 남긴다.
     * 언론사마다 "대법 X"/"대법원 X"처럼 어미나 조사, 인용부호 위치가 달라도 핵심 내용 글자 나열은
     * 비슷하게 남기 때문에, 이 상태에서 2-그램(연속 두 글자) 집합을 비교하면 표현이 달라도 겹치는 정도를
     * 안정적으로 잴 수 있다.
     */
    private String stripForSimilarity(String title) {
        return title
                .replaceAll("\\[[^\\]]*\\]", "")
                .replaceAll("[\"'“”‘’…·,.\\-–—()\\s]", "");
    }

    private Set<String> charBigrams(String s) {
        Set<String> set = new HashSet<>();
        for (int i = 0; i + 1 < s.length(); i++) set.add(s.substring(i, i + 2));
        return set;
    }

    /** 두 제목의 문자 2-그램 자카드 유사도(0~1). 완전히 다른 문구라도 겹치는 핵심 단어가 많으면 값이 높게 나온다. */
    private double titleSimilarity(String a, String b) {
        Set<String> setA = charBigrams(stripForSimilarity(a));
        Set<String> setB = charBigrams(stripForSimilarity(b));
        if (setA.isEmpty() || setB.isEmpty()) return 0;
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);
        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);
        return union.isEmpty() ? 0 : (double) intersection.size() / union.size();
    }

    /**
     * 보존 기간(3일) 내 기사를 오래된 순으로 훑어, 유사도가 임계값 이상인 기사가 여러 건이면
     * 가장 먼저 수집된 것만 남기고 나머지를 삭제한다(카테고리 구분 없이 전체 대상 — 같은 사건이
     * 카테고리를 넘나들며 실리는 경우까지 잡기 위함). fetchFeed()의 실시간 중복 체크(완전일치)를
     * 통과해 저장된 표현만 다른 중복도 여기서 함께 정리된다.
     */
    private int cleanupNearDuplicateTitles() {
        List<NewsArticle> recent = newsRepository.findByPublishedAtAfterOrderByPublishedAtAsc(
                LocalDateTime.now().minusDays(RETENTION_DAYS));
        if (recent.size() > MAX_DEDUP_CANDIDATES) {
            log.warn("[news] 근사중복 정리 대상 {}건 — 상한({}) 초과로 이번 주기는 건너뜀 (다음 주기에 재시도)",
                    recent.size(), MAX_DEDUP_CANDIDATES);
            return 0;
        }
        List<NewsArticle> kept = new ArrayList<>();
        List<Long> toDelete = new ArrayList<>();
        for (NewsArticle a : recent) {
            boolean isDuplicate = false;
            for (NewsArticle k : kept) {
                if (titleSimilarity(a.getTitle(), k.getTitle()) >= DUPLICATE_SIMILARITY_THRESHOLD) {
                    isDuplicate = true;
                    break;
                }
            }
            if (isDuplicate) toDelete.add(a.getId());
            else kept.add(a);
        }
        if (!toDelete.isEmpty()) newsRepository.deleteAllById(toDelete);
        return toDelete.size();
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
