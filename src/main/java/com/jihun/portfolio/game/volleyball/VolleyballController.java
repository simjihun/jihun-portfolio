package com.jihun.portfolio.game.volleyball;

import com.jihun.portfolio.game.domain.GameScore;
import com.jihun.portfolio.game.repository.GameScoreRepository;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 발리볼(몽이 발리볼) 랭킹 API.
 *
 * 실시간 물리 시뮬레이션(공 궤적, 충돌 판정, 캐릭터 이동)은 프레임마다 계산해야 해서
 * 서버 왕복으로는 자연스러운 반응속도를 낼 수 없다 — 그래서 게임 자체는 순수 프론트엔드
 * (Canvas + requestAnimationFrame)에서 전부 처리하고, 서버는 "도전이 끝난 뒤 기록만" 받는다.
 * 랭킹 지표는 한 판의 승패가 아니라 "패배 없이 몇 판을 연속으로 이겼는가"(연승 수)다.
 * 이기면 프론트에서 바로 다음 상대로 이어지고, 지는 순간의 연승 수가 최종 기록으로 저장된다.
 */
@RestController
@RequestMapping("/api/game/volleyball")
public class VolleyballController {

    private static final String GAME_TYPE = "VOLLEYBALL";
    private static final int MAX_REASONABLE_STREAK = 999; // 비정상적으로 큰 값(조작 시도) 방어

    private final GameScoreRepository repository;

    public VolleyballController(GameScoreRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/score")
    public Map<String, Object> saveScore(@RequestBody ScoreRequest req) {
        String nickname = (req.nickname() == null || req.nickname().isBlank()) ? "익명" : req.nickname().trim();
        if (nickname.length() > 20) nickname = nickname.substring(0, 20);
        int streak = Math.max(0, Math.min(req.streak(), MAX_REASONABLE_STREAK));
        GameScore saved = repository.save(new GameScore(GAME_TYPE, nickname, streak));
        return Map.of("id", saved.getId(), "metric", saved.getMetric());
    }

    @GetMapping("/ranking")
    public List<GameScore> ranking() {
        return repository.findTop10ByGameTypeOrderByMetricDesc(GAME_TYPE);
    }

    public record ScoreRequest(String nickname, int streak) {}
}
