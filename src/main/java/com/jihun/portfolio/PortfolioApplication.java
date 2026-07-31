package com.jihun.portfolio;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * jihun 포트폴리오 애플리케이션 시작점.
 *
 * 구조: 기능(프로젝트)별로 하위 패키지를 추가해가는 방식으로 확장한다.
 *   com.jihun.portfolio.message  → 메시지 발송 시스템 (MMS)
 *   com.jihun.portfolio.news     → AI 뉴스 (RSS 수집 + 카테고리 보드)
 *   ...
 *
 * @EnableScheduling: 뉴스 주기 수집 등 스케줄러 활성화
 */
@SpringBootApplication
@EnableScheduling
public class PortfolioApplication {

    public static void main(String[] args) {
        SpringApplication.run(PortfolioApplication.class, args);
    }
}
