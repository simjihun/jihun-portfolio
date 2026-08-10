package com.jihun.portfolio.game.repository;

import com.jihun.portfolio.game.domain.GameScore;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GameScoreRepository extends JpaRepository<GameScore, Long> {

    /** 해당 게임의 상위 10개 기록 — 숫자야구는 시도 횟수가 적을수록 좋음(오름차순) */
    List<GameScore> findTop10ByGameTypeOrderByMetricAsc(String gameType);

    /** 해당 게임의 상위 10개 기록 — 발리볼은 랠리 길이가 길수록 좋음(내림차순) */
    List<GameScore> findTop10ByGameTypeOrderByMetricDesc(String gameType);
}
