package com.jihun.portfolio.game.omok;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 오목 REST API (사람 vs AI).
 */
@RestController
@RequestMapping("/api/game/omok")
public class OmokController {

    private final OmokService service;

    public OmokController(OmokService service) {
        this.service = service;
    }

    @PostMapping("/new")
    public Map<String, Object> newGame(@RequestBody NewGameRequest req) {
        char human = "W".equalsIgnoreCase(req.humanColor()) ? 'W' : 'B';
        String difficulty = req.difficulty() == null ? "MEDIUM" : req.difficulty().toUpperCase();
        return toResponse(service.create(human, difficulty, req.timeLimitSeconds()));
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable String id) {
        return toResponse(service.get(id));
    }

    @PostMapping("/{id}/move")
    public Map<String, Object> move(@PathVariable String id, @RequestBody MoveRequest req) {
        try {
            return toResponse(service.move(id, req.x(), req.y()));
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
    public record MoveRequest(int x, int y) {}

    private Map<String, Object> toResponse(OmokGame g) {
        char[][] b = g.getBoard();
        String[] rows = new String[OmokGame.SIZE];
        for (int i = 0; i < OmokGame.SIZE; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < OmokGame.SIZE; j++) sb.append(b[i][j] == 0 ? '.' : b[i][j]);
            rows[i] = sb.toString();
        }
        Map<String, Object> res = new java.util.HashMap<>();
        res.put("id", g.getId());
        res.put("board", rows);
        res.put("currentPlayer", String.valueOf(g.getCurrentPlayer()));
        res.put("status", g.getStatus());
        res.put("moveCount", g.getMoveCount());
        res.put("humanColor", String.valueOf(g.getHumanColor()));
        res.put("aiColor", String.valueOf(g.getAiColor()));
        res.put("difficulty", g.getDifficulty());
        res.put("timeLimitSeconds", g.getTimeLimitSeconds());
        res.put("turnStartedAt", g.getTurnStartedAt());
        return res;
    }
}
