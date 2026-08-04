package com.jihun.portfolio.game.omok;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 오목 대국 진행 서비스 (사람 vs AI).
 * 대국은 재접속이 필요 없는 단일 세션이라 DB 대신 메모리에만 보관한다.
 */
@Service
public class OmokService {

    private final Map<String, OmokGame> games = new ConcurrentHashMap<>();
    private final OmokAiService aiService;

    public OmokService(OmokAiService aiService) {
        this.aiService = aiService;
    }

    /** 새 대국 생성. AI가 흑돌(선공)이면 생성 즉시 첫 수를 둔다. */
    public OmokGame create(char humanColor, String difficulty, Integer timeLimitSeconds) {
        String id = UUID.randomUUID().toString().substring(0, 8);
        char aiColor = humanColor == 'B' ? 'W' : 'B';
        OmokGame g = new OmokGame(id, humanColor, aiColor, difficulty, timeLimitSeconds);
        games.put(id, g);
        if (aiColor == 'B') {
            applyAiMove(g);
        }
        g.touchTurn();
        return g;
    }

    public OmokGame get(String id) {
        OmokGame g = games.get(id);
        if (g == null) throw new IllegalArgumentException("대국을 찾을 수 없습니다");
        return g;
    }

    public OmokGame move(String id, int x, int y) {
        OmokGame g = get(id);
        if (!"PLAYING".equals(g.getStatus())) return g;
        if (g.getCurrentPlayer() != g.getHumanColor()) {
            throw new IllegalArgumentException("AI 차례입니다");
        }
        placeStone(g, x, y, g.getHumanColor());
        if ("PLAYING".equals(g.getStatus())) {
            applyAiMove(g);
        }
        return g;
    }

    /** 제한시간이 지난 상태에서 호출되면, 사람 차례를 무작위 수로 자동 진행한다. */
    public OmokGame timeout(String id) {
        OmokGame g = get(id);
        if (!"PLAYING".equals(g.getStatus())) return g;
        if (g.getCurrentPlayer() != g.getHumanColor()) return g;
        if (g.getTimeLimitSeconds() == null) return g;

        long elapsedSec = (System.currentTimeMillis() - g.getTurnStartedAt()) / 1000;
        if (elapsedSec < g.getTimeLimitSeconds()) return g; // 아직 시간 안됨 — 무시

        int[] mv = aiService.pickMove(g.getBoard(), g.getHumanColor(), g.getAiColor(), "EASY");
        placeStone(g, mv[0], mv[1], g.getHumanColor());
        if ("PLAYING".equals(g.getStatus())) {
            applyAiMove(g);
        }
        return g;
    }

    private void applyAiMove(OmokGame g) {
        int[] mv = aiService.pickMove(g.getBoard(), g.getAiColor(), g.getHumanColor(), g.getDifficulty());
        placeStone(g, mv[0], mv[1], g.getAiColor());
    }

    private void placeStone(OmokGame g, int x, int y, char player) {
        if (x < 0 || x >= OmokGame.SIZE || y < 0 || y >= OmokGame.SIZE) {
            throw new IllegalArgumentException("범위를 벗어났습니다");
        }
        if (g.getBoard()[y][x] != 0) {
            throw new IllegalArgumentException("이미 돌이 놓인 자리입니다");
        }
        g.getBoard()[y][x] = player;
        g.setMoveCount(g.getMoveCount() + 1);

        if (checkWin(g.getBoard(), x, y, player)) {
            g.setStatus(player == 'B' ? "BLACK_WIN" : "WHITE_WIN");
        } else if (g.getMoveCount() >= OmokGame.SIZE * OmokGame.SIZE) {
            g.setStatus("DRAW");
        } else {
            g.setCurrentPlayer(player == 'B' ? 'W' : 'B');
            g.touchTurn();
        }
    }

    private boolean checkWin(char[][] board, int x, int y, char p) {
        int[][] dirs = {{1, 0}, {0, 1}, {1, 1}, {1, -1}};
        for (int[] d : dirs) {
            int count = 1;
            count += countDir(board, x, y, d[0], d[1], p);
            count += countDir(board, x, y, -d[0], -d[1], p);
            if (count >= 5) return true;
        }
        return false;
    }

    private int countDir(char[][] board, int x, int y, int dx, int dy, char p) {
        int c = 0, cx = x + dx, cy = y + dy;
        while (cx >= 0 && cx < OmokGame.SIZE && cy >= 0 && cy < OmokGame.SIZE && board[cy][cx] == p) {
            c++; cx += dx; cy += dy;
        }
        return c;
    }
}
