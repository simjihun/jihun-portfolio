package com.jihun.portfolio.game.klondike;

import com.jihun.portfolio.game.domain.GameScore;
import com.jihun.portfolio.game.repository.GameScoreRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 클론다이크(고전 솔리테어) 랭킹 API.
 *
 * 카드 로직(카드 뽑기, 이동 유효성, 뒤집기, 승리·막힘 판정)은 전부 프론트엔드에서 계산한다 —
 * 프리셀과 동일한 구조. 서버는 "한 판이 끝난 뒤" 결과만 기록한다.
 *
 * 난이도별 랭킹을 나누기 위해 gameType을 "KLONDIKE_EASY" / "KLONDIKE_MEDIUM" / "KLONDIKE_HARD"로 저장한다.
 * metric은 이동 횟수(적을수록 좋음 → 오름차순), detail은 "mm:ss" 소요 시간 표시 문자열이다.
 */
@RestController
@RequestMapping("/api/game/klondike")
public class KlondikeController {

    private static final String GAME_TYPE_PREFIX = "KLONDIKE_";
    private static final Set<String> VALID_DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final int MAX_REASONABLE_MOVES = 5000;
    private static final int MAX_REASONABLE_SECONDS = 24 * 60 * 60;

    private final GameScoreRepository repository;

    public KlondikeController(GameScoreRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/score")
    public Map<String, Object> saveScore(@RequestBody ScoreRequest req) {
        String difficulty = VALID_DIFFICULTIES.contains(req.difficulty()) ? req.difficulty() : "EASY";
        String nickname = (req.nickname() == null || req.nickname().isBlank()) ? "익명" : req.nickname().trim();
        if (nickname.length() > 20) nickname = nickname.substring(0, 20);

        int moves = Math.max(1, Math.min(req.moves(), MAX_REASONABLE_MOVES));
        int seconds = Math.max(0, Math.min(req.seconds(), MAX_REASONABLE_SECONDS));
        String detail = String.format("%d:%02d", seconds / 60, seconds % 60);

        GameScore saved = repository.save(new GameScore(GAME_TYPE_PREFIX + difficulty, nickname, moves, detail));
        return Map.of("id", saved.getId(), "metric", saved.getMetric(), "detail", detail);
    }

    @GetMapping("/ranking")
    public List<GameScore> ranking(@RequestParam(defaultValue = "EASY") String difficulty) {
        String safe = VALID_DIFFICULTIES.contains(difficulty) ? difficulty : "EASY";
        return repository.findTop10ByGameTypeOrderByMetricAsc(GAME_TYPE_PREFIX + safe);
    }

    public record ScoreRequest(String nickname, String difficulty, int moves, int seconds) {}
}
