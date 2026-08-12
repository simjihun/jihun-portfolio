package com.jihun.portfolio.news.repository;

import com.jihun.portfolio.news.domain.NewsArticle;
import com.jihun.portfolio.news.domain.NewsCategory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface NewsRepository extends JpaRepository<NewsArticle, Long> {

    boolean existsByLink(String link);

    /** 제목이 완전히 같은 기사(언론사만 다른 경우 포함) 중복 감지용 */
    boolean existsByTitle(String title);

    /** 카테고리별 최신 기사 */
    List<NewsArticle> findTop30ByCategoryOrderByPublishedAtDesc(NewsCategory category);

    /** 전체 최신 기사 (주요 뉴스 풀 확보용, 카테고리 균형 배치에 사용) */
    List<NewsArticle> findTop20ByOrderByPublishedAtDesc();

    /** 최근 속보 (상단 속보 스트립용) */
    List<NewsArticle> findTop6ByBreakingTrueAndPublishedAtAfterOrderByPublishedAtDesc(LocalDateTime after);

    /** 보존 기간 지난 기사 삭제 */
    long deleteByPublishedAtBefore(LocalDateTime before);

    long countByFetchedAtAfter(LocalDateTime after);

    /** 정규화 기준 중복 제목 정리용 — 보존 기간 내 전체를 오래된 순으로 훑는다 */
    List<NewsArticle> findByPublishedAtAfterOrderByPublishedAtAsc(LocalDateTime after);
}
