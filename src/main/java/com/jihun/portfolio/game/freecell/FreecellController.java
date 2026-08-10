package com.jihun.portfolio.game.freecell;

import com.jihun.portfolio.game.domain.GameScore;
import com.jihun.portfolio.game.repository.GameScoreRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 프리셀 랭킹 API.
 *
 * 카드 로직(뭉치 이동 유효성, 서플무브 용량 계산, 안전 자동정리, 승리 판정)은 전부 프론트엔드에서
 * 계산한다 — 완전 공개 정보 게임이라 서버가 상태를 들고 있을 이유가 없고, 클릭 반응성도 왕복 없이
 * 즉시 처리하는 편이 낫다. 서버는 "한 판이 끝난 뒤" 결과만 기록한다.
 *
 * 난이도(오픈칸 개수)마다 별도 랭킹을 두기 위해, GameScore 공용 테이블의 gameType을
 * "FREECELL_EASY" / "FREECELL_MEDIUM" / "FREECELL_HARD"로 나눠서 저장한다.
 * metric은 이동 횟수(적을수록 좋음 → 오름차순), detail은 "mm:ss" 소요 시간 표시 문자열이다.
 */
@RestController
@RequestMapping("/api/game/freecell")
public class FreecellController {

    private static final String GAME_TYPE_PREFIX = "FREECELL_";
    private static final Set<String> VALID_DIFFICULTIES = Set.of("EASY", "MEDIUM", "HARD");
    private static final int MAX_REASONABLE_MOVES = 5000; // 비정상적으로 큰 값(조작 시도) 방어
    private static final int MAX_REASONABLE_SECONDS = 24 * 60 * 60;

    private final GameScoreRepository repository;

    public FreecellController(GameScoreRepository repository) {
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
