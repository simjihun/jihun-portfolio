package com.jihun.portfolio.game.omok;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 오목 게임 진행 서비스.
 * 대국은 재접속이 필요 없는 단일 세션이라 DB 대신 메모리에만 보관한다.
 * (서버 재배포 시 진행 중이던 대국은 사라지는데, 포트폴리오 규모에서는 문제없는 트레이드오프)
 */
@Service
public class OmokService {

    private final Map<String, OmokGame> games = new ConcurrentHashMap<>();

    public OmokGame create() {
        String id = UUID.randomUUID().toString().substring(0, 8);
        OmokGame g = new OmokGame(id);
        games.put(id, g);
        return g;
    }

    public OmokGame get(String id) {
        OmokGame g = games.get(id);
        if (g == null) throw new IllegalArgumentException("게임을 찾을 수 없습니다");
        return g;
    }

    public OmokGame move(String id, int x, int y) {
        OmokGame g = get(id);
        if (!"PLAYING".equals(g.getStatus())) return g;
        if (x < 0 || x >= OmokGame.SIZE || y < 0 || y >= OmokGame.SIZE) {
            throw new IllegalArgumentException("범위를 벗어났습니다");
        }
        if (g.getBoard()[y][x] != 0) {
            throw new IllegalArgumentException("이미 돌이 놓인 자리입니다");
        }

        char player = g.getCurrentPlayer();
        g.getBoard()[y][x] = player;
        g.setMoveCount(g.getMoveCount() + 1);

        if (checkWin(g.getBoard(), x, y, player)) {
            g.setStatus(player == 'B' ? "BLACK_WIN" : "WHITE_WIN");
        } else if (g.getMoveCount() >= OmokGame.SIZE * OmokGame.SIZE) {
            g.setStatus("DRAW");
        } else {
            g.setCurrentPlayer(player == 'B' ? 'W' : 'B');
        }
        return g;
    }

    /** 방금 둔 돌(x,y)을 기준으로 가로·세로·대각선 4방향을 검사해 5목 완성을 판단한다. */
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
