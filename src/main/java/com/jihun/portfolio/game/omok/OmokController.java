package com.jihun.portfolio.game.omok;

import com.jihun.portfolio.game.domain.GameScore;
import com.jihun.portfolio.game.repository.GameScoreRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 오목 REST API (사람 vs AI).
 */
@RestController
@RequestMapping("/api/game/omok")
public class OmokController {

    private static final String GAME_TYPE_PREFIX = "OMOK_";
    private static final Set<String> VALID_DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final int MAX_REASONABLE_SECONDS = 24 * 60 * 60;

    private final OmokService service;
    private final GameScoreRepository scoreRepository;

    public OmokController(OmokService service, GameScoreRepository scoreRepository) {
        this.service = service;
        this.scoreRepository = scoreRepository;
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

    /** 내가 이긴 대국만 랭킹에 기록한다. 착수 수·난이도는 서버가 들고 있는 값을 그대로 쓰고,
     *  소요시간만 프론트엔드가 측정해 전달한다(프리셀·클론다이크와 동일한 방식). */
    @PostMapping("/{id}/score")
    public Map<String, Object> saveScore(@PathVariable String id, @RequestBody ScoreRequest req) {
        OmokGame g = service.get(id);
        boolean humanWon = ("BLACK_WIN".equals(g.getStatus()) && g.getHumanColor() == 'B')
                || ("WHITE_WIN".equals(g.getStatus()) && g.getHumanColor() == 'W');
        if (!humanWon) return Map.of("error", "승리한 대국만 랭킹에 등록할 수 있습니다");

        String nickname = (req.nickname() == null || req.nickname().isBlank()) ? "익명" : req.nickname().trim();
        if (nickname.length() > 20) nickname = nickname.substring(0, 20);
        String difficulty = VALID_DIFFICULTIES.contains(g.getDifficulty()) ? g.getDifficulty() : "MEDIUM";
        int seconds = Math.max(0, Math.min(req.seconds(), MAX_REASONABLE_SECONDS));
        String detail = String.format("%d:%02d", seconds / 60, seconds % 60);

        GameScore saved = scoreRepository.save(new GameScore(GAME_TYPE_PREFIX + difficulty, nickname, g.getMoveCount(), detail));
        return Map.of("id", saved.getId(), "metric", saved.getMetric(), "detail", detail);
    }

    @GetMapping("/ranking")
    public List<GameScore> ranking(@RequestParam(defaultValue = "MEDIUM") String difficulty) {
        String safe = VALID_DIFFICULTIES.contains(difficulty) ? difficulty : "MEDIUM";
        return scoreRepository.findTop10ByGameTypeOrderByMetricAsc(GAME_TYPE_PREFIX + safe);
    }

    public record NewGameRequest(String humanColor, String difficulty, Integer timeLimitSeconds) {}
    public record MoveRequest(int x, int y) {}
    public record ScoreRequest(String nickname, int seconds) {}

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
