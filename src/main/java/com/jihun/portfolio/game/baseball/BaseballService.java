package com.jihun.portfolio.game.baseball;

import com.jihun.portfolio.game.domain.GameScore;
import com.jihun.portfolio.game.repository.GameScoreRepository;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 숨자야구(Up & Down) 진행 서비스.
 * 1~9 중 중복 없는 4자리 정답을 서버가 정해 놓고, 스트라이크/볼을 판정해 준다.
 */
@Service
public class BaseballService {

    private static final int MAX_REASONABLE_SECONDS = 24 * 60 * 60;

    private final Map<String, BaseballGame> games = new ConcurrentHashMap<>();
    private final GameScoreRepository scoreRepository;
    private final SecureRandom random = new SecureRandom();

    public BaseballService(GameScoreRepository scoreRepository) {
        this.scoreRepository = scoreRepository;
    }

    public BaseballGame create() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        BaseballGame g = new BaseballGame(id, generateAnswer());
        games.put(id, g);
        return g;
    }

    private String generateAnswer() {
        List<Integer> digits = new ArrayList<>();
        for (int i = 1; i <= 9; i++) digits.add(i);
        Collections.shuffle(digits, random);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) sb.append(digits.get(i));
        return sb.toString();
    }

    public BaseballGame get(String id) {
        BaseballGame g = games.get(id);
        if (g == null) throw new IllegalArgumentException("게임을 찾을 수 없습니다");
        return g;
    }

    public BaseballGame guess(String id, String guess) {
        BaseballGame g = get(id);
        if (!"PLAYING".equals(g.getStatus())) return g;
        if (guess == null || !guess.matches("[1-9]{4}") || hasDuplicateDigits(guess)) {
            throw new IllegalArgumentException("1~9 숫자 4자리를 중복 없이 입력하세요");
        }
        int strikes = 0, balls = 0;
        String answer = g.getAnswer();
        for (int i = 0; i < 4; i++) {
            char c = guess.charAt(i);
            if (answer.charAt(i) == c) strikes++;
            else if (answer.indexOf(c) >= 0) balls++;
        }
        g.setAttempts(g.getAttempts() + 1);
        g.getHistory().add(guess + ":" + strikes + "S" + balls + "B");
        if (strikes == 4) g.setStatus("WON");
        return g;
    }

    private boolean hasDuplicateDigits(String s) {
        return s.chars().distinct().count() != s.length();
    }

    /** seconds는 정답을 맞히기까지 걸린 시간(프론트엔드가 시작 시각 기준으로 측정해 전달). */
    public GameScore saveScore(String nickname, int attempts, int seconds) {
        String nick = (nickname == null || nickname.isBlank()) ? "익명" : nickname.strip();
        if (nick.length() > 12) nick = nick.substring(0, 12);
        int safeSeconds = Math.max(0, Math.min(seconds, MAX_REASONABLE_SECONDS));
        String detail = String.format("%d:%02d", safeSeconds / 60, safeSeconds % 60);
        return scoreRepository.save(new GameScore("BASEBALL", nick, attempts, detail));
    }

    public List<GameScore> ranking() {
        return scoreRepository.findTop10ByGameTypeOrderByMetricAsc("BASEBALL");
    }
}
