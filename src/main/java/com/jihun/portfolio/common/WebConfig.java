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
        // 게임 — 하나의 통합 페이지에서 탭으로 개별 게임을 전환해 보여준다
        registry.addViewController("/game").setViewName("forward:/game.html");
        registry.addViewController("/game/omok").setViewName("forward:/game.html");
        registry.addViewController("/game/baseball").setViewName("forward:/game.html");
        registry.addViewController("/game/janggi").setViewName("forward:/game.html");
        // 주식 AI 대시보드
        registry.addViewController("/stock").setViewName("forward:/stock.html");
    }
}
