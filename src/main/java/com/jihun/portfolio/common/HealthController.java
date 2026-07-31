package com.jihun.portfolio.common;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 공통 헬스체크 엔드포인트.
 * CI/CD 배포 검증과 nginx/로드밸런서의 서버 생존 확인에 사용한다.
 */
@RestController
public class HealthController {

    @GetMapping("/healthz")
    public Map<String, String> healthz() {
        return Map.of("status", "ok");
    }
}
