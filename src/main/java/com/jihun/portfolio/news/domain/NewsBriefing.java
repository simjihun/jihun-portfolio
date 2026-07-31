package com.jihun.portfolio.news.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/**
 * 카테고리별 AI 3줄 브리핑.
 * 카테고리당 1건만 유지하며(카테고리가 PK), 주기적으로 새 내용으로 덮어쓴다.
 */
@Entity
@Table(name = "news_briefing")
public class NewsBriefing {

    @Id
    @Column(length = 20)
    private String category;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private LocalDateTime generatedAt;

    protected NewsBriefing() {}

    public NewsBriefing(NewsCategory category, String content) {
        this.category = category.name();
        this.content = content;
        this.generatedAt = LocalDateTime.now();
    }

    public String getCategory() { return category; }
    public String getContent() { return content; }
    public LocalDateTime getGeneratedAt() { return generatedAt; }
    public String getCategoryLabel() { return NewsCategory.valueOf(category).getLabel(); }
}
