package com.jihun.portfolio.game.spider;

import com.jihun.portfolio.game.domain.GameScore;
import com.jihun.portfolio.game.repository.GameScoreRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 스파이더 솔리테어 랭킹 API.
 *
 * 카드 로직(뭉치 이동 유효성, 완성된 세트 자동 수거, 승리·막힘 판정)은 전부 프론트엔드에서 계산한다 —
 * 프리셀·클론다이크와 동일한 구조. 서버는 "한 판이 끝난 뒤" 결과만 기록한다.
 *
 * 난이도는 진짜 스파이더 솔리테어의 정식 변형 그대로 사용한다(가짜 난이도 아님) — 사용하는 무늬 수가
 * 1종/2종/4종으로 늘어날수록 완성 조건이 까다로워진다. gameType은 "SPIDER_EASY/MEDIUM/HARD",
 * metric은 이동 횟수(적을수록 좋음 → 오름차순), detail은 "mm:ss" 소요 시간이다.
 */
@RestController
@RequestMapping("/api/game/spider")
public class SpiderController {

    private static final String GAME_TYPE_PREFIX = "SPIDER_";
    private static final Set<String> VALID_DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final int MAX_REASONABLE_MOVES = 5000;
    private static final int MAX_REASONABLE_SECONDS = 24 * 60 * 60;

    private final GameScoreRepository repository;

    public SpiderController(GameScoreRepository repository) {
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
