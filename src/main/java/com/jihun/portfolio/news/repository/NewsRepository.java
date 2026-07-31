package com.jihun.portfolio.news.repository;

import com.jihun.portfolio.news.domain.NewsArticle;
import com.jihun.portfolio.news.domain.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByLink(String link);

    /** 카테고리별 최신 기사 */
    List<NewsArticle> findTop30ByCategoryOrderByPublishedAtDesc(NewsCategory category);

    /** 전체 최신 기사 (주요 뉴스 하이라이트용) */
    List<NewsArticle> findTop8ByOrderByPublishedAtDesc();

    /** 최근 속보 (상단 속보 스트립용) */
    List<NewsArticle> findTop6ByBreakingTrueAndPublishedAtAfterOrderByPublishedAtDesc(LocalDateTime after);

    /** 보존 기간 지난 기사 삭제 */
    long deleteByPublishedAtBefore(LocalDateTime before);

    long countByFetchedAtAfter(LocalDateTime after);
}
