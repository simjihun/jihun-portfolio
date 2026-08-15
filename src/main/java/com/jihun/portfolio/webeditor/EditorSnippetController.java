package com.jihun.portfolio.webeditor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 웹 에디터 스니펫 REST API. 로그인 없이 누구나 저장·조회할 수 있는 공개 API다(CodePen과 동일한
 * 익명 공개 모델). 수정/삭제 엔드포인트는 의도적으로 두지 않는다(불변) — 소유권을 증명할 방법이
 * 없는 상태에서 수정/삭제를 열어두면 누구나 남의 스니펫을 지울 수 있게 된다.
 */
@RestController
@RequestMapping("/api/webeditor")
public class EditorSnippetController {

    // 악의적인 초대형 페이로드로 DB가 불어나는 것을 막기 위한 필드별 상한(넉넉하게 잡음)
    private static final int MAX_TITLE_LEN = 100;
    private static final int MAX_CODE_LEN = 200_000; // 필드당 약 200KB
    private static final int MAX_LIBS = 10;
    private static final int MAX_LIB_URL_LEN = 300;

    private final EditorSnippetRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public EditorSnippetController(EditorSnippetRepository repository) {
        this.repository = repository;
    }

    public record SaveRequest(String title, String template, String html, String css, String js, List<String> libs) {}
    public record SnippetSummary(Long id, String title, String template, LocalDateTime createdAt) {}
    public record SnippetDetail(Long id, String title, String template, String html, String css, String js,
                                 List<String> libs, LocalDateTime createdAt) {}

    @PostMapping("/snippets")
    public ResponseEntity<?> save(@RequestBody SaveRequest req) {
        if (req.title() == null || req.title().isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "제목을 입력해주세요."));
        }
        if (req.title().length() > MAX_TITLE_LEN) {
            return ResponseEntity.badRequest().body(Map.of("error", "제목은 " + MAX_TITLE_LEN + "자 이내로 입력해주세요."));
        }
        EditorTemplate template;
        try {
            template = EditorTemplate.valueOf(req.template());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "알 수 없는 템플릿입니다."));
        }
        String html = trimTo(req.html(), MAX_CODE_LEN);
        String css = trimTo(req.css(), MAX_CODE_LEN);
        String js = trimTo(req.js(), MAX_CODE_LEN);
        if ((html == null || html.isBlank()) && (js == null || js.isBlank())) {
            return ResponseEntity.badRequest().body(Map.of("error", "HTML 또는 JS 중 하나는 내용이 있어야 합니다."));
        }
        List<String> libs = req.libs() == null ? List.of() : req.libs().stream()
                .filter(u -> u != null && !u.isBlank() && u.length() <= MAX_LIB_URL_LEN)
                .limit(MAX_LIBS)
                .toList();

        String libsJson;
        try {
            libsJson = mapper.writeValueAsString(libs);
        } catch (Exception e) {
            libsJson = "[]";
        }

        EditorSnippet saved = repository.save(new EditorSnippet(req.title().strip(), template, html, css, js, libsJson));
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of("id", saved.getId()));
    }

    @GetMapping("/snippets")
    public List<SnippetSummary> list(@RequestParam(defaultValue = "30") int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), 60);
        return repository.findAllByOrderByCreatedAtDesc(PageRequest.of(0, safeLimit)).stream()
                .map(s -> new SnippetSummary(s.getId(), s.getTitle(), s.getTemplate().name(), s.getCreatedAt()))
                .toList();
    }

    @GetMapping("/snippets/{id}")
    public ResponseEntity<?> get(@PathVariable Long id) {
        // .map()과 .orElseGet() 두 분기가 서로 다른 바디 타입(SnippetDetail vs 에러 Map)을 반환해서
        // Optional<T>의 T를 하나로 추론하지 못해 컴파일 오류가 났었다 — 제네릭 타입을 ResponseEntity<?>로
        // 명시해 두 분기가 같은 타입으로 취급되게 한다.
        return repository.findById(id)
                .<ResponseEntity<?>>map(s -> ResponseEntity.ok(new SnippetDetail(
                        s.getId(), s.getTitle(), s.getTemplate().name(), s.getHtml(), s.getCss(), s.getJs(),
                        parseLibs(s.getLibsJson()), s.getCreatedAt())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "존재하지 않는 스니펫입니다.")));
    }

    @SuppressWarnings("unchecked")
    private List<String> parseLibs(String libsJson) {
        try {
            return mapper.readValue(libsJson, List.class);
        } catch (Exception e) {
            return List.of();
        }
    }

    private String trimTo(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max) : s;
    }
}
