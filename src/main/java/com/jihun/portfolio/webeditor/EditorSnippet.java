package com.jihun.portfolio.webeditor;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 웹 에디터에 저장된 코드 조각(스니펫). CodePen처럼 로그인 없이 누구나 공개로 저장하고,
 * 저장된 목록은 모든 방문자가 함께 볼 수 있다.
 *
 * 수정/삭제는 지원하지 않는다(불변) — 로그인이 없어 "이 스니펫이 내 것"이라는 소유권을
 * 증명할 방법이 없기 때문에, 남의 코드를 고쳐서 저장하면 항상 새 스니펫으로 등록된다
 * (CodePen의 "Fork" 개념과 동일).
 *
 * html/css/js/libsJson은 @Lob + columnDefinition="LONGTEXT" 명시 — @Lob만으로는 MySQL에서
 * 충분히 큰 컬럼이 안 만들어져 저장이 실패하는 사례가 있었다(StockAiBriefing에서 확인된 문제,
 * CLAUDE.md 'Gemini API 호출 정책' 참고 — 같은 원인이라 여기서도 동일하게 명시한다).
 */
@Entity
@Table(name = "editor_snippet", indexes = {
        @Index(name = "idx_editor_snippet_created", columnList = "createdAt")
})
public class EditorSnippet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private EditorTemplate template;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String html;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String css;

    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String js;

    /** 외부 CDN 라이브러리 URL 목록 — JSON 배열 문자열로 저장(예: ["https://cdn.../lib.js"]) */
    @Lob
    @Column(columnDefinition = "LONGTEXT")
    private String libsJson;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected EditorSnippet() {}

    public EditorSnippet(String title, EditorTemplate template, String html, String css, String js, String libsJson) {
        this.title = title;
        this.template = template;
        this.html = html;
        this.css = css;
        this.js = js;
        this.libsJson = libsJson;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public EditorTemplate getTemplate() { return template; }
    public String getHtml() { return html; }
    public String getCss() { return css; }
    public String getJs() { return js; }
    public String getLibsJson() { return libsJson; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
