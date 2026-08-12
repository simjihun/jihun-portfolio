package com.jihun.portfolio.admin;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** 관리자 대시보드용 시스템 정보 API. SecurityConfig의 "/api/admin/**" 규칙으로 ROLE_ADMIN만 접근 가능. */
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AwsInstanceInfoService awsInstanceInfoService;
    private final DatabaseInfoService databaseInfoService;

    public AdminDashboardController(AwsInstanceInfoService awsInstanceInfoService, DatabaseInfoService databaseInfoService) {
        this.awsInstanceInfoService = awsInstanceInfoService;
        this.databaseInfoService = databaseInfoService;
    }

    @GetMapping("/aws")
    public Map<String, Object> aws() {
        return awsInstanceInfoService.getInfo();
    }

    @GetMapping("/database")
    public Map<String, Object> database() {
        return databaseInfoService.getInfo();
    }
}
