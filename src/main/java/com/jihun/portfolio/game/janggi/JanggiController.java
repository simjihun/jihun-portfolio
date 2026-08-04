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
        return toResponse(service.create(human, difficulty, req.timeLimitSeconds()));
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

    @PostMapping("/{id}/move")
    public Map<String, Object> move(@PathVariable String id, @RequestBody MoveRequest req) {
        try {
            return toResponse(service.move(id, req.fromX(), req.fromY(), req.toX(), req.toY()));
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    /** 프론트엔드 타이머가 만료되었을 때 호출 — 서버가 실제 경과시간을 다시 확인해 처리한다. */
    @PostMapping("/{id}/timeout")
    public Map<String, Object> timeout(@PathVariable String id) {
        return toResponse(service.timeout(id));
    }

    public record NewGameRequest(String humanColor, String difficulty, Integer timeLimitSeconds) {}
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
