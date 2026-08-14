package com.jihun.portfolio.news.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 수집된 뉴스 기사 1건.
 *
 * 저작권 고려: 기사 본문/요약문은 저장하지 않는다.
 * 제목 + 언론사 + 시각 + 원문 링크만 보관하고, 클릭 시 언론사 원문으로 연결한다.
 *
 * link/title 인덱스: NewsFetchService가 30분마다 새 RSS 항목 하나하나를 existsByLink()/
 * existsByTitle()로 중복 체크하는데, 인덱스가 없어 매번 테이블 전체를 스캔하고 있었다. 기사가
 * 쌓일수록 이 체크가 느려져 결국 수집 작업(fetchAll) 한 번이 오래 걸리게 됐고, 그게 다른
 * @Scheduled 작업(AI 브리핑 등)이 스케줄러 스레드를 못 받는 원인 중 하나였다.
 */
@Entity
@Table(name = "news_article", indexes = {
        @Index(name = "idx_news_category_published", columnList = "category, publishedAt"),
        @Index(name = "idx_news_link", columnList = "link"),
        @Index(name = "idx_news_title", columnList = "title")
})
public class NewsArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NewsCategory category;

    @Column(nullable = false, length = 300)
    private String title;

    @Column(nullable = false, length = 600)
    private String link;          // 원문 링크 (중복 수집 판별 기준)

    @Column(nullable = false, length = 40)
    private String press;         // 언론사

    @Column(nullable = false)
    private LocalDateTime publishedAt;  // 기사 발행 시각

    @Column(nullable = false)
    private LocalDateTime fetchedAt;    // 수집 시각

    @Column(nullable = false)
    private boolean breaking;     // 속보/단독 여부 (제목 기반 감지)

    protected NewsArticle() {
        // JPA 기본 생성자
    }

    public NewsArticle(NewsCategory category, String title, String link,
                       String press, LocalDateTime publishedAt, boolean breaking) {
        this.category = category;
        this.title = title;
        this.link = link;
        this.press = press;
        this.publishedAt = publishedAt;
        this.fetchedAt = LocalDateTime.now();
        this.breaking = breaking;
    }

    // --- Getter ---
    public Long getId() { return id; }
    public NewsCategory getCategory() { return category; }
    public String getCategoryLabel() { return category.getLabel(); }
    public String getTitle() { return title; }
    public String getLink() { return link; }
    public String getPress() { return press; }
    public LocalDateTime getPublishedAt() { return publishedAt; }
    public LocalDateTime getFetchedAt() { return fetchedAt; }
    public boolean isBreaking() { return breaking; }
}
