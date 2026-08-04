package com.jihun.portfolio.game.omok;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 오목 REST API. 보드는 문자열 배열(행당 문자열)로 직렬화해 보낸다.
 */
@RestController
@RequestMapping("/api/game/omok")
public class OmokController {

    private final OmokService service;

    public OmokController(OmokService service) {
        this.service = service;
    }

    @PostMapping("/new")
    public Map<String, Object> newGame() {
        return toResponse(service.create());
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

    public record MoveRequest(int x, int y) {}

    private Map<String, Object> toResponse(OmokGame g) {
        char[][] b = g.getBoard();
        String[] rows = new String[OmokGame.SIZE];
        for (int i = 0; i < OmokGame.SIZE; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < OmokGame.SIZE; j++) sb.append(b[i][j] == 0 ? '.' : b[i][j]);
            rows[i] = sb.toString();
        }
        return Map.of(
                "id", g.getId(),
                "board", rows,
                "currentPlayer", String.valueOf(g.getCurrentPlayer()),
                "status", g.getStatus(),
                "moveCount", g.getMoveCount()
        );
    }
}
