package com.jihun.portfolio.game.baseball;

import java.util.ArrayList;
import java.util.List;

/**
 * 숨자야구 한 판의 상태 (메모리 보관). 정답은 클라이언트에 절대 노출되지 않는다.
 */
public class BaseballGame {

    private final String id;
    private final String answer; // 1~9 중복 없는 4자리
    private final List<String> history = new ArrayList<>(); // 예: "1234:1S2B"
    private String status = "PLAYING"; // PLAYING, WON
    private int attempts = 0;

    public BaseballGame(String id, String answer) {
        this.id = id;
        this.answer = answer;
    }

    public String getId() { return id; }
    public String getAnswer() { return answer; }
    public List<String> getHistory() { return history; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public int getAttempts() { return attempts; }
    public void setAttempts(int attempts) { this.attempts = attempts; }
}
