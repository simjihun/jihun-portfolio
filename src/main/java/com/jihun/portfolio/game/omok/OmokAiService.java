package com.jihun.portfolio.game.omok;

import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * 오목 AI 엔진.
 *
 * 외부 AI API를 전혀 쓰지 않고, 순수 자바 로직(패턴 휴리스틱 평가 + 간이화된 미니맥스)만으로 동작한다.
 * 비용이 전혀 들지 않고 응답도 즉시적이라는 장점이 있다.
 *
 * - 입력된 돌 주변만 후보로 삼아 탐색 범위를 줄인다.
 * - 각 경우의 수를 4방향 연속돌 패턴(열린/닫힌 생 / 4목/5목)으로 점수를 매긴다.
 * - EASY: 상대의 즉승모만 막고 나머지는 무작위.
 * - MEDIUM: 공격/수비 점수를 합산한 1수 휴리스틱 최선수.
 * - HARD: 상위 후보들을 추려 상대의 최선 응수까지 1수 더 내다보는 2플라이 탐색.
 */
@Service
public class OmokAiService {

    private static final int SIZE = OmokGame.SIZE;
    private static final int WIN_SCORE = 100_000;

    private final SecureRandom random = new SecureRandom();

    public int[] pickMove(char[][] board, char aiColor, char humanColor, String difficulty) {
        List<int[]> candidates = candidateCells(board);
        if (candidates.isEmpty()) {
            return new int[]{SIZE / 2, SIZE / 2}; // 첫 수는 중앙
        }
        return switch (difficulty == null ? "MEDIUM" : difficulty) {
            case "EASY" -> pickEasy(board, candidates, humanColor);
            case "HARD" -> pickHard(board, candidates, aiColor, humanColor);
            default -> pickMedium(board, candidates, aiColor, humanColor);
        };
    }

    /** 기존 돌 주변 2칸 이내의 빈 칸만 후보로 삼아 탐색 범위를 줄인다. */
    private List<int[]> candidateCells(char[][] board) {
        boolean[][] mark = new boolean[SIZE][SIZE];
        List<int[]> result = new ArrayList<>();
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (board[y][x] == 0) continue;
                for (int dy = -2; dy <= 2; dy++) {
                    for (int dx = -2; dx <= 2; dx++) {
                        int nx = x + dx, ny = y + dy;
                        if (nx < 0 || nx >= SIZE || ny < 0 || ny >= SIZE) continue;
                        if (board[ny][nx] != 0 || mark[ny][nx]) continue;
                        mark[ny][nx] = true;
                        result.add(new int[]{nx, ny});
                    }
                }
            }
        }
        return result;
    }

    private int[] pickEasy(char[][] board, List<int[]> candidates, char human) {
        for (int[] c : candidates) {
            if (scoreCell(board, c[0], c[1], human) >= WIN_SCORE) return c; // 상대 즉승방지용으로만 차단
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    private int[] pickMedium(char[][] board, List<int[]> candidates, char ai, char human) {
        int[] best = candidates.get(0);
        double bestScore = Double.NEGATIVE_INFINITY;
        for (int[] c : candidates) {
            double score = scoreCell(board, c[0], c[1], ai) + scoreCell(board, c[0], c[1], human) * 0.9;
            if (score > bestScore) { bestScore = score; best = c; }
        }
        return best;
    }

    private int[] pickHard(char[][] board, List<int[]> candidates, char ai, char human) {
        List<int[]> top = topByScore(board, candidates, ai, human, 12);
        int[] best = top.get(0);
        double bestVal = Double.NEGATIVE_INFINITY;

        for (int[] c : top) {
            double myScore = scoreCell(board, c[0], c[1], ai);
            if (myScore >= WIN_SCORE) return c; // 즉승수는 바로 택함

            char[][] after = copy(board);
            after[c[1]][c[0]] = ai;
            List<int[]> humanCandidates = candidateCells(after);
            double humanBest = 0;
            for (int[] hc : topByScore(after, humanCandidates, human, ai, 8)) {
                humanBest = Math.max(humanBest, scoreCell(after, hc[0], hc[1], human));
            }
            double val = myScore - humanBest;
            if (val > bestVal) { bestVal = val; best = c; }
        }
        return best;
    }

    private List<int[]> topByScore(char[][] board, List<int[]> candidates, char self, char opp, int n) {
        candidates.sort((a, b) -> {
            double sa = scoreCell(board, a[0], a[1], self) + scoreCell(board, a[0], a[1], opp) * 0.9;
            double sb = scoreCell(board, b[0], b[1], self) + scoreCell(board, b[0], b[1], opp) * 0.9;
            return Double.compare(sb, sa);
        });
        return candidates.subList(0, Math.min(n, candidates.size()));
    }

    private char[][] copy(char[][] board) {
        char[][] c = new char[SIZE][SIZE];
        for (int i = 0; i < SIZE; i++) c[i] = board[i].clone();
        return c;
    }

    /** (x,y)에 color가 다음 수를 둔다고 가정했을 때의 가치를 4방향 패턴으로 평가한다. */
    private double scoreCell(char[][] board, int x, int y, char color) {
        if (board[y][x] != 0) return 0;
        double total = 0;
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            total += scoreLine(board, x, y, d[0], d[1], color);
        }
        return total;
    }

    private double scoreLine(char[][] board, int x, int y, int dx, int dy, char color) {
        int count = 1;
        int cx = x + dx, cy = y + dy;
        while (inRange(cx, cy) && board[cy][cx] == color) { count++; cx += dx; cy += dy; }
        boolean openA = inRange(cx, cy) && board[cy][cx] == 0;

        cx = x - dx; cy = y - dy;
        while (inRange(cx, cy) && board[cy][cx] == color) { count++; cx -= dx; cy -= dy; }
        boolean openB = inRange(cx, cy) && board[cy][cx] == 0;

        boolean open = openA && openB;
        if (count >= 5) return WIN_SCORE;
        if (count == 4) return open ? 50_000 : 8_000;
        if (count == 3) return open ? 3_000 : 400;
        if (count == 2) return open ? 200 : 40;
        return open ? 15 : 5;
    }

    private boolean inRange(int x, int y) {
        return x >= 0 && x < SIZE && y >= 0 && y < SIZE;
    }
}
