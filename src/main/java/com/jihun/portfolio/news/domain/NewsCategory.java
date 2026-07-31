package com.jihun.portfolio.news.domain;

/**
 * 뉴스 카테고리.
 * label은 화면 표시용 한글 이름.
 */
public enum NewsCategory {
    POLITICS("정치"),
    ECONOMY("경제"),
    SOCIETY("사회"),
    LIFE("생활·문화"),
    WORLD("세계"),
    ENTERTAINMENT("연예"),
    STOCK("주식");

    private final String label;

    NewsCategory(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
