package com.jihun.portfolio.game.janggi;

/**
 * 장기 대국 하나의 상태 (메모리 보관). 사람이 AI와 대국하는 구조이다.
 *
 * 보드 표기: String[10][9], null=빈칸, 그외에는 2글자 코드(색+기물).
 *  색: H(한, 봉/상단) / O(초, 답/하단)
 *  기물: K 궁(장) A 사 상(E) 마(N) 차(R) 포(C) 졸·병(P)
 */
public class JanggiGame {

    public static final int COLS = 9;
    public static final int ROWS = 10;

    private final String id;
    private final String[][] board = new String[ROWS][COLS];
    private String currentPlayer = "H"; // 한이 선수
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

    public JanggiGame(String id, String humanColor, String aiColor, String difficulty, Integer timeLimitSeconds) {
        this.id = id;
        this.humanColor = humanColor;
        this.aiColor = aiColor;
        this.difficulty = difficulty;
        this.timeLimitSeconds = timeLimitSeconds;
        setupBoard();
    }

    private void setupBoard() {
        String[] backH = {"HR", "HN", "HE", "HA", null, "HA", "HE", "HN", "HR"};
        for (int c = 0; c < COLS; c++) board[0][c] = backH[c];
        board[1][4] = "HK";
        board[2][1] = "HC"; board[2][7] = "HC"; // 포는 마와 같은 세로줄(1,7)에 위치
        for (int c = 0; c < COLS; c += 2) board[3][c] = "HP";

        String[] backO = {"OR", "ON", "OE", "OA", null, "OA", "OE", "ON", "OR"};
        for (int c = 0; c < COLS; c++) board[9][c] = backO[c];
        board[8][4] = "OK";
        board[7][1] = "OC"; board[7][7] = "OC"; // 포는 마와 같은 세로줄(1,7)에 위치
        for (int c = 0; c < COLS; c += 2) board[6][c] = "OP";
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
