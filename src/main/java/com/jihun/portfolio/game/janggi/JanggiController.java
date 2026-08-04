package com.jihun.portfolio.game.janggi;

import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 장기 REST API (사람 vs AI).
 */
@RestController
@RequestMapping("/api/game/janggi")
public class JanggiController {

    private final JanggiService service;

    public JanggiController(JanggiService service) {
        this.service = service;
    }

    @PostMapping("/new")
    public Map<String, Object> newGame(@RequestBody NewGameRequest req) {
        String human = "O".equalsIgnoreCase(req.humanColor()) ? "O" : "H";
        String difficulty = req.difficulty() == null ? "MEDIUM" : req.difficulty().toUpperCase();
        String humanFormation = req.humanFormation() == null ? "MSMS" : req.humanFormation().toUpperCase();
        String aiFormation = req.aiFormation() == null ? "MSMS" : req.aiFormation().toUpperCase();
        return toResponse(service.create(human, difficulty, req.timeLimitSeconds(), humanFormation, aiFormation));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return toResponse(service.get(id));
    }

    /** 선택한 기물이 이동 가능한 칸 목록 — 클릭 2단계(선택→이동) 흐름의 하이라이트용. */
    @GetMapping("/{id}/moves")
    public Map<String, Object> moves(@PathVariable String id, @RequestParam int x, @RequestParam int y) {
        List<int[]> dest = service.legalMovesFrom(id, x, y).stream()
                .map(m -> new int[]{m.toX(), m.toY()})
                .toList();
        return Map.of("destinations", dest);
    }

    /** 사람의 수만 적용한다. AI 응수는 별도로 /ai-move를 호출해야 한다(장군 상태를 화면에 보여줄 틈을 주기 위함). */
    @PostMapping("/{id}/move")
    public Map<String, Object> move(@PathVariable String id, @RequestBody MoveRequest req) {
        try {
            return toResponse(service.move(id, req.fromX(), req.fromY(), req.toX(), req.toY()));
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    /** AI 차례일 때 AI의 수를 진행시킨다. 프론트가 사람 수 응답을 렌더링한 뒤 잠깐 텀을 두고 호출한다. */
    @PostMapping("/{id}/ai-move")
    public Map<String, Object> aiMove(@PathVariable String id) {
        try {
            return toResponse(service.aiMove(id));
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    /** 프론트엔드 타이머가 만료되었을 때 호출 — 서버가 실제 경과시간을 다시 확인해 사람 차례만 강제 진행한다. */
    @PostMapping("/{id}/timeout")
    public Map<String, Object> timeout(@PathVariable String id) {
        return toResponse(service.timeout(id));
    }

    public record NewGameRequest(String humanColor, String difficulty, Integer timeLimitSeconds,
                                  String humanFormation, String aiFormation) {}
    public record MoveRequest(int fromX, int fromY, int toX, int toY) {}

    private Map<String, Object> toResponse(JanggiGame g) {
        Map<String, Object> res = new HashMap<>();
        res.put("id", g.getId());
        res.put("board", g.getBoard());
        res.put("currentPlayer", g.getCurrentPlayer());
        res.put("status", g.getStatus());
        res.put("moveCount", g.getMoveCount());
        res.put("humanColor", g.getHumanColor());
        res.put("aiColor", g.getAiColor());
        res.put("difficulty", g.getDifficulty());
        res.put("timeLimitSeconds", g.getTimeLimitSeconds());
        res.put("turnStartedAt", g.getTurnStartedAt());
        res.put("checkColor", g.getCheckColor());
        Map<String, Object> lastMove = null;
        if (g.getLastFromX() != null) {
            lastMove = new HashMap<>();
            lastMove.put("fromX", g.getLastFromX());
            lastMove.put("fromY", g.getLastFromY());
            lastMove.put("toX", g.getLastToX());
            lastMove.put("toY", g.getLastToY());
        }
        res.put("lastMove", lastMove);
        return res;
    }
}
