package com.jihun.portfolio.game.janggi;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 장기 AI 엔진.
 *
 * 오목과 동일하게 외부 AI API를 쓰지 않고, 순수 자바 로직(기물 가치 평가 + 간이 미니맥스)만으로 동작한다.
 * - 기물 가치: 차13, 포7, 마5, 상/사3, 병(졸)2, 궁은 승패를 합법수 소진 여부로 판정하므로 평가에서는 큰 값만 부여.
 * - EASY: 잡을 수 있는 수를 종종 선호하되 대체로 무작위.
 * - MEDIUM: 이동 후 보드의 기물가치 합을 평가하는 1수 최선수.
 * - HARD: 잡는 수 위주로 후보를 추려, 상대의 최선 응수까지 내다보는 2플라이 탐색.
 */
@Service
public class JanggiAiService {

    private static final Map<String, Integer> PIECE_VALUE = Map.of(
            "K", 10_000, "R", 13, "C", 7, "N", 5, "A", 3, "E", 3, "P", 2
    );

    private final SecureRandom random = new SecureRandom();

    public JanggiRules.Move pickMove(String[][] board, String aiColor, String humanColor, String difficulty) {
        List<JanggiRules.Move> moves = JanggiRules.legalMoves(board, aiColor);
        if (moves.isEmpty()) return null; // 합법수 없음 = 패배 (서비스단에서 처리)
        return switch (difficulty == null ? "MEDIUM" : difficulty) {
            case "EASY" -> pickEasy(board, moves);
            case "HARD" -> pickHard(board, moves, aiColor, humanColor);
            default -> pickMedium(board, moves, aiColor);
        };
    }

    private JanggiRules.Move pickEasy(String[][] board, List<JanggiRules.Move> moves) {
        List<JanggiRules.Move> captures = new ArrayList<>();
        for (JanggiRules.Move m : moves) if (board[m.toY()][m.toX()] != null) captures.add(m);
        if (!captures.isEmpty() && random.nextInt(3) != 0) {
            return captures.get(random.nextInt(captures.size()));
        }
        return moves.get(random.nextInt(moves.size()));
    }

    private JanggiRules.Move pickMedium(String[][] board, List<JanggiRules.Move> moves, String aiColor) {
        JanggiRules.Move best = moves.get(0);
        int bestScore = Integer.MIN_VALUE;
        for (JanggiRules.Move m : moves) {
            String[][] after = JanggiRules.applied(board, m.fromX(), m.fromY(), m.toX(), m.toY());
            int score = evaluate(after, aiColor);
            if (score > bestScore) { bestScore = score; best = m; }
        }
        return best;
    }

    private JanggiRules.Move pickHard(String[][] board, List<JanggiRules.Move> moves, String aiColor, String humanColor) {
        JanggiRules.Move best = moves.get(0);
        int bestVal = Integer.MIN_VALUE;
        List<JanggiRules.Move> top = topByCapture(board, moves, 14);

        for (JanggiRules.Move m : top) {
            String[][] after = JanggiRules.applied(board, m.fromX(), m.fromY(), m.toX(), m.toY());
            List<JanggiRules.Move> oppMoves = JanggiRules.legalMoves(after, humanColor);
            int worstForMe;
            if (oppMoves.isEmpty()) {
                worstForMe = evaluate(after, aiColor) + 5_000; // 상대가 응수 불가 = 사실상 승리
            } else {
                worstForMe = Integer.MAX_VALUE;
                for (JanggiRules.Move om : topByCapture(after, oppMoves, 10)) {
                    String[][] after2 = JanggiRules.applied(after, om.fromX(), om.fromY(), om.toX(), om.toY());
                    worstForMe = Math.min(worstForMe, evaluate(after2, aiColor));
                }
            }
            if (worstForMe > bestVal) { bestVal = worstForMe; best = m; }
        }
        return best;
    }

    private List<JanggiRules.Move> topByCapture(String[][] board, List<JanggiRules.Move> moves, int n) {
        List<JanggiRules.Move> sorted = new ArrayList<>(moves);
        sorted.sort((a, b) -> Integer.compare(captureValue(board, b), captureValue(board, a)));
        return sorted.subList(0, Math.min(n, sorted.size()));
    }

    private int captureValue(String[][] board, JanggiRules.Move m) {
        String target = board[m.toY()][m.toX()];
        return target == null ? 0 : PIECE_VALUE.getOrDefault(target.substring(1, 2), 0);
    }

    private int evaluate(String[][] board, String color) {
        int score = 0;
        for (int y = 0; y < JanggiGame.ROWS; y++) {
            for (int x = 0; x < JanggiGame.COLS; x++) {
                String p = board[y][x];
                if (p == null) continue;
                int v = PIECE_VALUE.getOrDefault(p.substring(1, 2), 0);
                score += p.startsWith(color) ? v : -v;
            }
        }
        return score;
    }
}
