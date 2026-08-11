package com.jihun.portfolio.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 깔끔한 URL 매핑 설정.
 * 확장자(.html) 없는 주소를 실제 정적 파일로 포워딩한다.
 * 새 기능 등록 시 여기에 한 줄만 추가하면 됨.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 메시징
        registry.addViewController("/mms").setViewName("forward:/mms.html");
        // 뉴스
        registry.addViewController("/news").setViewName("forward:/news.html");
        // 지도 — 맛집/부동산을 하나의 통합 페이지에서 토글로 전환해 보여준다
        registry.addViewController("/map").setViewName("forward:/map.html");
        registry.addViewController("/map/food").setViewName("forward:/map.html");
        registry.addViewController("/map/estate").setViewName("forward:/map.html");
        // 웹 게임(상위 분류) — /game 아래 웹게임(숫자야구·발리볼) / board(오목·장기) / card(프리셀·클론다이크) 3갈래
        registry.addViewController("/game").setViewName("forward:/game.html");
        registry.addViewController("/game/baseball").setViewName("forward:/game.html");
        registry.addViewController("/game/volleyball").setViewName("forward:/game.html");
        registry.addViewController("/game/board").setViewName("forward:/board.html");
        registry.addViewController("/game/board/omok").setViewName("forward:/board.html");
        registry.addViewController("/game/board/janggi").setViewName("forward:/board.html");
        registry.addViewController("/game/card").setViewName("forward:/cardgame.html");
        registry.addViewController("/game/card/freecell").setViewName("forward:/cardgame.html");
        registry.addViewController("/game/card/klondike").setViewName("forward:/cardgame.html");
        // 주식 AI 대시보드
        registry.addViewController("/stock").setViewName("forward:/stock.html");
    }
}
