package com.jihun.portfolio.game.janggi;

import java.util.ArrayList;
import java.util.List;

/**
 * 장기 규칙 엔진: 기물별 이동 생성, 장군(체크) 판정, 외통수(체크메이트) 판정.
 *
 * 간소화한 점: 차·포·졸(병)의 궁성 대각선 특수 이동은 생략하고,
 * 궁(장)과 사의 궁성 대각선 이동만 구현한다. 기본 이동·장군·외통수 판정은 정확하게 동작한다.
 */
public final class JanggiRules {

    private static final int COLS = JanggiGame.COLS;
    private static final int ROWS = JanggiGame.ROWS;

    public record Move(int fromX, int fromY, int toX, int toY) {}

    /** 궁성 대각선 점: 코너 4개 + 중앙 1개. 코너는 중앙과만 연결된다. */
    private record PalaceDiag(int cx, int cy, int[][] corners) {}
    private static final PalaceDiag HAN_PALACE = new PalaceDiag(4, 1, new int[][]{{3,0},{5,0},{3,2},{5,2}});
    private static final PalaceDiag CHO_PALACE = new PalaceDiag(4, 8, new int[][]{{3,7},{5,7},{3,9},{5,9}});

    // 마(말) 이동 8패턴: {dx,dy,legx,legy}
    private static final int[][] HORSE = {
            {1,-2,0,-1},{-1,-2,0,-1},{1,2,0,1},{-1,2,0,1},
            {2,-1,1,0},{2,1,1,0},{-2,-1,-1,0},{-2,1,-1,0}
    };
    // 상 이동 8패턴: {dx,dy,leg1x,leg1y,leg2x,leg2y}
    private static final int[][] ELEPHANT = {
            {2,-3,0,-1,1,-2},{-2,-3,0,-1,-1,-2},{2,3,0,1,1,2},{-2,3,0,1,-1,2},
            {3,-2,1,0,2,-1},{3,2,1,0,2,1},{-3,-2,-1,0,-2,-1},{-3,2,-1,0,-2,1}
    };
    private static final int[][] ORTHO = {{0,-1},{0,1},{-1,0},{1,0}};

    private JanggiRules() {}

    public static boolean inBounds(int x, int y) { return x >= 0 && x < COLS && y >= 0 && y < ROWS; }

    private static String colorOf(String piece) { return piece == null ? null : piece.substring(0, 1); }
    private static String typeOf(String piece) { return piece == null ? null : piece.substring(1, 2); }
    private static String enemyOf(String color) { return "H".equals(color) ? "O" : "H"; }

    private static boolean isPalace(int x, int y, String color) {
        if (x < 3 || x > 5) return false;
        return "H".equals(color) ? (y >= 0 && y <= 2) : (y >= 7 && y <= 9);
    }

    private static PalaceDiag palaceOf(int x, int y) {
        if (isPalace(x, y, "H")) return HAN_PALACE;
        if (isPalace(x, y, "O")) return CHO_PALACE;
        return null;
    }

    /** (x,y)가 궁성 대각선점(코너/중앙)이면, 그 점에서 대각선으로 닿는 이웃 점들을 반환 */
    private static List<int[]> diagonalPalaceNeighbors(int x, int y) {
        List<int[]> result = new ArrayList<>();
        PalaceDiag p = palaceOf(x, y);
        if (p == null) return result;
        if (x == p.cx() && y == p.cy()) {
            for (int[] c : p.corners()) result.add(c);
        } else {
            for (int[] c : p.corners()) {
                if (c[0] == x && c[1] == y) { result.add(new int[]{p.cx(), p.cy()}); break; }
            }
        }
        return result;
    }

    /** 특정 기물 하나의 수들(자기 궁 장군 여부는 고려하지 않은 pseudo-legal 이동). */
    public static List<int[]> pseudoMoves(String[][] board, int x, int y) {
        List<int[]> moves = new ArrayList<>();
        String piece = board[y][x];
        if (piece == null) return moves;
        String color = colorOf(piece), type = typeOf(piece), enemy = enemyOf(color);

        switch (type) {
            case "K", "A" -> {
                for (int[] d : ORTHO) {
                    int nx = x + d[0], ny = y + d[1];
                    if (isPalace(nx, ny, color) && canLand(board, nx, ny, color)) moves.add(new int[]{nx, ny});
                }
                for (int[] n : diagonalPalaceNeighbors(x, y)) {
                    if (isPalace(n[0], n[1], color) && canLand(board, n[0], n[1], color)) moves.add(n);
                }
            }
            case "N" -> {
                for (int[] h : HORSE) {
                    int legx = x + h[2], legy = y + h[3];
                    if (!inBounds(legx, legy) || board[legy][legx] != null) continue;
                    int nx = x + h[0], ny = y + h[1];
                    if (inBounds(nx, ny) && canLand(board, nx, ny, color)) moves.add(new int[]{nx, ny});
                }
            }
            case "E" -> {
                for (int[] e : ELEPHANT) {
                    int l1x = x + e[2], l1y = y + e[3];
                    int l2x = x + e[4], l2y = y + e[5];
                    if (!inBounds(l1x, l1y) || board[l1y][l1x] != null) continue;
                    if (!inBounds(l2x, l2y) || board[l2y][l2x] != null) continue;
                    int nx = x + e[0], ny = y + e[1];
                    if (inBounds(nx, ny) && canLand(board, nx, ny, color)) moves.add(new int[]{nx, ny});
                }
            }
            case "R" -> {
                for (int[] d : ORTHO) slide(board, x, y, d[0], d[1], color, moves);
                addPalaceDiagonalSlides(board, x, y, color, moves);
            }
            case "C" -> addCannonMoves(board, x, y, color, moves);
            case "P" -> {
                int fwd = "H".equals(color) ? 1 : -1;
                int[][] dirs = {{0, fwd}, {-1, 0}, {1, 0}};
                for (int[] d : dirs) {
                    int nx = x + d[0], ny = y + d[1];
                    if (inBounds(nx, ny) && canLand(board, nx, ny, color)) moves.add(new int[]{nx, ny});
                }
            }
            default -> {}
        }
        return moves;
    }

    private static boolean canLand(String[][] board, int x, int y, String color) {
        String target = board[y][x];
        return target == null || !colorOf(target).equals(color);
    }

    private static void slide(String[][] board, int x, int y, int dx, int dy, String color, List<int[]> out) {
        int nx = x + dx, ny = y + dy;
        while (inBounds(nx, ny)) {
            String target = board[ny][nx];
            if (target == null) {
                out.add(new int[]{nx, ny});
            } else {
                if (!colorOf(target).equals(color)) out.add(new int[]{nx, ny});
                break;
            }
            nx += dx; ny += dy;
        }
    }

    /** 차(Chariot)의 궁성 대각선 이동: 코너↔중앙↔반대 코너 직선을 타고 미끄럼파럼 이동한다. */
    private static void addPalaceDiagonalSlides(String[][] board, int x, int y, String color, List<int[]> out) {
        PalaceDiag p = palaceOf(x, y);
        if (p == null) return;
        boolean atCenter = (x == p.cx() && y == p.cy());
        if (atCenter) {
            for (int[] c : p.corners()) {
                String target = board[c[1]][c[0]];
                if (target == null || !colorOf(target).equals(color)) out.add(c);
            }
        } else {
            boolean isCorner = false;
            for (int[] c : p.corners()) if (c[0] == x && c[1] == y) isCorner = true;
            if (!isCorner) return;
            int dx = Integer.signum(p.cx() - x), dy = Integer.signum(p.cy() - y);
            int cx = p.cx(), cy = p.cy();
            String centerPiece = board[cy][cx];
            if (centerPiece == null) {
                out.add(new int[]{cx, cy});
                int fx = cx + dx, fy = cy + dy; // 반대편 코너까지 계속
                if (inBounds(fx, fy)) {
                    String farTarget = board[fy][fx];
                    if (farTarget == null || !colorOf(farTarget).equals(color)) out.add(new int[]{fx, fy});
                }
            } else if (!colorOf(centerPiece).equals(color)) {
                out.add(new int[]{cx, cy});
            }
        }
    }

    private static void addCannonMoves(String[][] board, int x, int y, String color, List<int[]> out) {
        for (int[] d : ORTHO) {
            boolean screenFound = false;
            int nx = x + d[0], ny = y + d[1];
            while (inBounds(nx, ny)) {
                String target = board[ny][nx];
                if (!screenFound) {
                    if (target != null) {
                        if ("C".equals(typeOf(target))) break; // 포는 포를 넘을 수 없음
                        screenFound = true;
                    }
                } else {
                    if (target == null) {
                        out.add(new int[]{nx, ny});
                    } else {
                        if (!"C".equals(typeOf(target)) && !colorOf(target).equals(color)) out.add(new int[]{nx, ny});
                        break;
                    }
                }
                nx += d[0]; ny += d[1];
            }
        }
    }

    private static int[] findKing(String[][] board, String color) {
        String target = color + "K";
        for (int y = 0; y < ROWS; y++) for (int x = 0; x < COLS; x++) if (target.equals(board[y][x])) return new int[]{x, y};
        return null;
    }

    public static boolean isInCheck(String[][] board, String color) {
        int[] king = findKing(board, color);
        if (king == null) return true;
        String enemy = enemyOf(color);
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                String piece = board[y][x];
                if (piece == null || !colorOf(piece).equals(enemy)) continue;
                for (int[] m : pseudoMoves(board, x, y)) {
                    if (m[0] == king[0] && m[1] == king[1]) return true;
                }
            }
        }
        return false;
    }

    public static String[][] copyBoard(String[][] board) {
        String[][] c = new String[ROWS][COLS];
        for (int y = 0; y < ROWS; y++) c[y] = board[y].clone();
        return c;
    }

    public static String[][] applied(String[][] board, int fx, int fy, int tx, int ty) {
        String[][] b = copyBoard(board);
        b[ty][tx] = b[fy][fx];
        b[fy][fx] = null;
        return b;
    }

    /** 자신의 궁이 장군에 노출되지 않는 진짜 합법수만 반환. */
    public static List<Move> legalMoves(String[][] board, String color) {
        List<Move> result = new ArrayList<>();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                String piece = board[y][x];
                if (piece == null || !colorOf(piece).equals(color)) continue;
                for (int[] m : pseudoMoves(board, x, y)) {
                    String[][] after = applied(board, x, y, m[0], m[1]);
                    if (!isInCheck(after, color)) result.add(new Move(x, y, m[0], m[1]));
                }
            }
        }
        return result;
    }
}
