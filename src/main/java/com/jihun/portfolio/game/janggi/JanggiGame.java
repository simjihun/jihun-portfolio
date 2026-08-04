package com.jihun.portfolio.game.janggi;

/**
 * 장기 대국 하나의 상태 (메모리 보관). 사람이 AI와 대국하는 구조이다.
 *
 * 보드 표기: String[10][9], null=빈칸, 그외에는 2글자 코드(색+기물).
 *  색: H(한, 봉/상단) / O(초, 답/하단)
 *  기물: K 궁(장) A 사 상(E) 마(N) 차(R) 포(C) 졸·병(P)
 *
 * 포진(마상 배치)은 4가지 중 선택 가능: MSMS(마상마상) / MSSM(마상상마) / SMSM(상마상마) / SMMS(상마마상)
 * — 각 코드는 왼쪽 페어(1,2열) + 오른쪽 페어(6,7열)를 왼쪽부터 순서대로 읽은 것이다.
 */
public class JanggiGame {

    public static final int COLS = 9;
    public static final int ROWS = 10;

    private final String id;
    private final String[][] board = new String[ROWS][COLS];
    private String currentPlayer = "O"; // 장기는 초(楚)가 항상 선수
    private String status = "PLAYING";  // PLAYING, H_WIN, O_WIN
    private int moveCount = 0;

    private final String humanColor; // "H" or "O"
    private final String aiColor;
    private final String difficulty;
    private final Integer timeLimitSeconds;
    private long turnStartedAt = System.currentTimeMillis();

    // 마지막 수 (프론트 하이라이트용)
    private Integer lastFromX;
    private Integer lastFromY;
    private Integer lastToX;
    private Integer lastToY;

    public JanggiGame(String id, String humanColor, String aiColor, String difficulty, Integer timeLimitSeconds,
                       String formationH, String formationO) {
        this.id = id;
        this.humanColor = humanColor;
        this.aiColor = aiColor;
        this.difficulty = difficulty;
        this.timeLimitSeconds = timeLimitSeconds;
        setupBoard(formationH, formationO);
    }

    private void setupBoard(String formationH, String formationO) {
        String[] backH = buildBackRank("H", formationH);
        for (int c = 0; c < COLS; c++) board[0][c] = backH[c];
        board[1][4] = "HK";
        board[2][1] = "HC"; board[2][7] = "HC"; // 포는 마와 같은 세로줄(1,7)에 위치
        for (int c = 0; c < COLS; c += 2) board[3][c] = "HP";

        String[] backO = buildBackRank("O", formationO);
        for (int c = 0; c < COLS; c++) board[9][c] = backO[c];
        board[8][4] = "OK";
        board[7][1] = "OC"; board[7][7] = "OC"; // 포는 마와 같은 세로줄(1,7)에 위치
        for (int c = 0; c < COLS; c += 2) board[6][c] = "OP";
    }

    private String[] buildBackRank(String color, String formation) {
        char[] f = formationPieces(formation); // [1열, 2열, 6열, 7열]
        String[] rank = new String[COLS];
        rank[0] = color + "R";
        rank[1] = color + f[0];
        rank[2] = color + f[1];
        rank[3] = color + "A";
        rank[4] = null;
        rank[5] = color + "A";
        rank[6] = color + f[2];
        rank[7] = color + f[3];
        rank[8] = color + "R";
        return rank;
    }

    /** N=마(馬) E=상(象). 4가지 표준 포진. */
    private char[] formationPieces(String code) {
        if (code == null) code = "MSMS";
        return switch (code) {
            case "MSSM" -> new char[]{'N', 'E', 'E', 'N'}; // 마상상마
            case "SMSM" -> new char[]{'E', 'N', 'E', 'N'}; // 상마상마
            case "SMMS" -> new char[]{'E', 'N', 'N', 'E'}; // 상마마상
            default -> new char[]{'N', 'E', 'N', 'E'};      // 마상마상 (MSMS, 기본값)
        };
    }

    public void touchTurn() { this.turnStartedAt = System.currentTimeMillis(); }

    public void setLastMove(int fromX, int fromY, int toX, int toY) {
        this.lastFromX = fromX; this.lastFromY = fromY; this.lastToX = toX; this.lastToY = toY;
    }

    public String getId() { return id; }
    public String[][] getBoard() { return board; }
    public String getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(String currentPlayer) { this.currentPlayer = currentPlayer; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getMoveCount() { return moveCount; }
    public void setMoveCount(int moveCount) { this.moveCount = moveCount; }
    public String getHumanColor() { return humanColor; }
    public String getAiColor() { return aiColor; }
    public String getDifficulty() { return difficulty; }
    public Integer getTimeLimitSeconds() { return timeLimitSeconds; }
    public long getTurnStartedAt() { return turnStartedAt; }
    public Integer getLastFromX() { return lastFromX; }
    public Integer getLastFromY() { return lastFromY; }
    public Integer getLastToX() { return lastToX; }
    public Integer getLastToY() { return lastToY; }
}
