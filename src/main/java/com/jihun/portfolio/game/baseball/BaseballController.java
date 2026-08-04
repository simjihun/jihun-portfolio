package com.jihun.portfolio.game.baseball;

import com.jihun.portfolio.game.domain.GameScore;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/game/baseball")
public class BaseballController {

    private final BaseballService service;

    public BaseballController(BaseballService service) {
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

    @PostMapping("/{id}/guess")
    public Map<String, Object> guess(@PathVariable String id, @RequestBody GuessRequest req) {
        try {
            return toResponse(service.guess(id, req.guess()));
        } catch (IllegalArgumentException e) {
            return Map.of("error", e.getMessage());
        }
    }

    /** 4스트라이크(정답)로 승리한 뒤 닉네임을 받아 랭킹에 기록한다. */
    @PostMapping("/{id}/score")
    public GameScore saveScore(@PathVariable String id, @RequestBody ScoreRequest req) {
        BaseballGame g = service.get(id);
        return service.saveScore(req.nickname(), g.getAttempts());
    }

    @GetMapping("/ranking")
    public List<GameScore> ranking() {
        return service.ranking();
    }

    public record GuessRequest(String guess) {}
    public record ScoreRequest(String nickname) {}

    private Map<String, Object> toResponse(BaseballGame g) {
        return Map.of(
                "id", g.getId(),
                "history", g.getHistory(),
                "status", g.getStatus(),
                "attempts", g.getAttempts()
        );
    }
}
