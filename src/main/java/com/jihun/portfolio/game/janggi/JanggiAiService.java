package com.jihun.portfolio.game.janggi;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 장기 AI 엔진.
 *
 * 외부 AI API를 쓰지 않고, 순수 자바 로직(기물 가치 평가 + 알파베타 미니맥스)만으로 동작한다.
 * - 기물 가치: 차13, 포7, 마5, 상/사3, 병(졸)2, 궁은 별도 처리(승패는 합법수 소진 여부로 판정).
 * - EASY: 잡을 수 있는 수를 종종 선호하되 대체로 무작위 (의도적으로 약한 초급용).
 * - MEDIUM: 2플라이 완전탐색(내 수 + 상대 최선 응수까지 고려), 알파베타 가지치기.
 * - HARD: 4플라이 완전탐색(양쪽 한 번씩 더 내다봄), 알파베타 가지치기.
 *
 * 이전 버전은 "잡는 수 상위 N개만" 후보를 잘라내는 방식이라, 상대의 조용한 수(비-캡처 응수)를
 * 아예 못 보고 기물을 헛수로 내주는 문제가 있었다. 지금은 후보를 자르지 않고 전부 탐색하며,
 * 캡처 우선 정렬은 가지치기 효율을 높이는 용도로만 사용한다.
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

        if ("EASY".equals(difficulty)) {
            return pickEasy(board, moves);
        }
        int depth = "HARD".equals(difficulty) ? 4 : 2; // MEDIUM=2플라이, HARD=4플라이
        return pickBest(board, moves, aiColor, humanColor, depth);
    }

    private JanggiRules.Move pickEasy(String[][] board, List<JanggiRules.Move> moves) {
        List<JanggiRules.Move> captures = new ArrayList<>();
        for (JanggiRules.Move m : moves) if (board[m.toY()][m.toX()] != null) captures.add(m);
        if (!captures.isEmpty() && random.nextInt(3) != 0) {
            return captures.get(random.nextInt(captures.size()));
        }
        return moves.get(random.nextInt(moves.size()));
    }

    private JanggiRules.Move pickBest(String[][] board, List<JanggiRules.Move> moves, String aiColor, String humanColor, int depth) {
        JanggiRules.Move best = moves.get(0);
        int bestVal = Integer.MIN_VALUE;
        int alpha = Integer.MIN_VALUE;
        int beta = Integer.MAX_VALUE;
        for (JanggiRules.Move m : orderMoves(board, moves)) {
            String[][] after = JanggiRules.applied(board, m.fromX(), m.fromY(), m.toX(), m.toY());
            int val = minimax(after, humanColor, aiColor, depth - 1, alpha, beta);
            if (val > bestVal) { bestVal = val; best = m; }
            alpha = Math.max(alpha, bestVal);
        }
        return best;
    }

    /** toMove가 다음에 둘 차례인 국면을 aiColor 기준으로 평가하는 완전탐색 알파베타 미니맥스. */
    private int minimax(String[][] board, String toMove, String aiColor, int depth, int alpha, int beta) {
        List<JanggiRules.Move> moves = JanggiRules.legalMoves(board, toMove);
        if (moves.isEmpty()) {
            // 둘 수 없으면 그 즉시 패배: toMove가 AI라면 최악, 사람이라면 AI에게 최선
            return toMove.equals(aiColor) ? -50_000 : 50_000;
        }
        if (depth <= 0) return evaluate(board, aiColor);

        boolean maximizing = toMove.equals(aiColor);
        String next = "H".equals(toMove) ? "O" : "H";
        if (maximizing) {
            int value = Integer.MIN_VALUE;
            for (JanggiRules.Move m : orderMoves(board, moves)) {
                String[][] after = JanggiRules.applied(board, m.fromX(), m.fromY(), m.toX(), m.toY());
                value = Math.max(value, minimax(after, next, aiColor, depth - 1, alpha, beta));
                alpha = Math.max(alpha, value);
                if (beta <= alpha) break;
            }
            return value;
        } else {
            int value = Integer.MAX_VALUE;
            for (JanggiRules.Move m : orderMoves(board, moves)) {
                String[][] after = JanggiRules.applied(board, m.fromX(), m.fromY(), m.toX(), m.toY());
                value = Math.min(value, minimax(after, next, aiColor, depth - 1, alpha, beta));
                beta = Math.min(beta, value);
                if (beta <= alpha) break;
            }
            return value;
        }
    }

    /** 잡는 수를 앞쪽으로 정렬해 알파베타 가지치기 효율을 높인다 (수를 버리지 않고 순서만 바꾼다). */
    private List<JanggiRules.Move> orderMoves(String[][] board, List<JanggiRules.Move> moves) {
        List<JanggiRules.Move> sorted = new ArrayList<>(moves);
        sorted.sort((a, b) -> Integer.compare(captureValue(board, b), captureValue(board, a)));
        return sorted;
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
