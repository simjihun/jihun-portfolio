package com.jihun.portfolio.game.omok;

/**
 * 오목 대국 하나의 상태 (메모리 보관 — DB가 아니라 서버 메모리에만 존재하는 임시 대국실).
 * 한 브라우저를 같이 보는 두 사람이 번갈아가며 들어가는 핫시트(hotseat) 방식이다.
 */
public class OmokGame {

    public static final int SIZE = 15;

    private final String id;
    private final char[][] board = new char[SIZE][SIZE]; // 0 = 빈칸, 'B' = 흑돌, 'W' = 백돌
    private char currentPlayer = 'B'; // 흑돌이 먼저 둔다
    private String status = "PLAYING"; // PLAYING, BLACK_WIN, WHITE_WIN, DRAW
    private int moveCount = 0;

    public OmokGame(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public char[][] getBoard() { return board; }
    public char getCurrentPlayer() { return currentPlayer; }
    public void setCurrentPlayer(char currentPlayer) { this.currentPlayer = currentPlayer; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getMoveCount() { return moveCount; }
    public void setMoveCount(int moveCount) { this.moveCount = moveCount; }
}
