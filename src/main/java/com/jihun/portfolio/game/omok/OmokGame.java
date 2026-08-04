package com.jihun.portfolio.game.omok;

/**
 * 오목 대국 하나의 상태 (메모리 보관). 사람이 AI와 대국하는 구조이다.
 */
public class OmokGame {

    public static final int SIZE = 15;

    private final String id;
    private final char[][] board = new char[SIZE][SIZE]; // 0 = 빈칸, 'B' = 흑돌, 'W' = 백돌
    private char currentPlayer = 'B'; // 흑돌이 항상 먼저 둔다
    private String status = "PLAYING"; // PLAYING, BLACK_WIN, WHITE_WIN, DRAW
    private int moveCount = 0;

    private final char humanColor;
    private final char aiColor;
    private final String difficulty;      // EASY, MEDIUM, HARD
    private final Integer timeLimitSeconds; // null = 제한시간 없음
    private long turnStartedAt = System.currentTimeMillis();

    public OmokGame(String id, char humanColor, char aiColor, String difficulty, Integer timeLimitSeconds) {
        this.id = id;
        this.humanColor = humanColor;
        this.aiColor = aiColor;
        this.difficulty = difficulty;
        this.timeLimitSeconds = timeLimitSeconds;
    }

    /** 차례가 바뀜 때마다 호출해 타이머 기준점을 갱신한다. */
    public void touchTurn() {
        this.turnStartedAt = System.currentTimeMillis();
    }

    public String getId() { return id; }
    public char[][] getBoard() { return board; }
    public char getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(char currentPlayer) { this.currentPlayer = currentPlayer; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getMoveCount() { return moveCount; }
    public void setMoveCount(int moveCount) { this.moveCount = moveCount; }
    public char getHumanColor() { return humanColor; }
    public char getAiColor() { return aiColor; }
    public String getDifficulty() { return difficulty; }
    public Integer getTimeLimitSeconds() { return timeLimitSeconds; }
    public long getTurnStartedAt() { return turnStartedAt; }
}
