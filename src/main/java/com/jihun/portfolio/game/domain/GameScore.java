package com.jihun.portfolio.game.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * 게임별 기록(랭킹) 공통 엔티티.
 * gameType으로 여러 게임을 구분해 하나의 테이블을 공유한다.
 * metric은 게임마다 의미가 다릅(숨자야구: 시도 횟수, 적을수록 좋음).
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

    @Column(nullable = false)
    private LocalDateTime createdAt;

    protected GameScore() {
    }

    public GameScore(String gameType, String nickname, int metric) {
        this.gameType = gameType;
        this.nickname = nickname;
        this.metric = metric;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getGameType() { return gameType; }
    public String getNickname() { return nickname; }
    public int getMetric() { return metric; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
