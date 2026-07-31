package com.jihun.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * jihun 포트폴리오 애플리케이션 시작점.
 *
 * 구조: 기능(프로젝트)별로 하위 패키지를 추가해가는 방식으로 확장한다.
 *   com.jihun.portfolio.message  → 메시지 발송 시스템 (Message/MMS)
 *   com.jihun.portfolio.game     → (예정) 웹 게임
 *   com.jihun.portfolio.map      → (예정) 지도 연동
 *   ...
 */
@SpringBootApplication
public class PortfolioApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
    }
}
