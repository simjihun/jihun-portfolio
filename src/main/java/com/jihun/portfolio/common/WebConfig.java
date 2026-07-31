package com.jihun.portfolio.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 깔끔한 URL 매핑 설정.
 * 확장자(.html) 없는 주소를 실제 정적 파일로 포워딩한다.
 * (forward는 리다이렉트와 달리 주소창 URL이 그대로 유지된다)
 *
 * 새 기능을 추가할 때마다 여기에 한 줄씩 등록:
 *   registry.addViewController("/game").setViewName("forward:/game.html");
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/mms").setViewName("forward:/mms.html");
    }
}
