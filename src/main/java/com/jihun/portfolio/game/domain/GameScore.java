package com.jihun.portfolio.game.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 게임별 기록(랭킹) 공통 엔티티.
 * gameType으로 여러 게임을 구분해 하나의 테이블을 공유한다.
 * metric은 게임마다 의미가 다름(숫자야구: 시도 횟수, 적을수록 좋음. 발리볼: 승수, 많을수록 좋음).
 * detail은 metric 하나로 표현이 안 되는 부가 표시용 텍스트(예: 발리볼의 "7:13" 최종 스코어) — 없으면 null.
 */
@Entity
@Table(name = "game_score", indexes = @Index(name = "idx_game_type_metric", columnList = "gameType, metric"))
public class GameScore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String gameType;

    @Column(nullable = false, length = 20)
    private String nickname;

    @Column(nullable = false)
    private int metric;

    @Column(length = 30)
    private String detail;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected GameScore() {
    }

    public GameScore(String gameType, String nickname, int metric) {
        this(gameType, nickname, metric, null);
    }

    public GameScore(String gameType, String nickname, int metric, String detail) {
        this.gameType = gameType;
        this.nickname = nickname;
        this.metric = metric;
        this.detail = detail;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getGameType() { return gameType; }
    public String getNickname() { return nickname; }
    public int getMetric() { return metric; }
    public String getDetail() { return detail; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
