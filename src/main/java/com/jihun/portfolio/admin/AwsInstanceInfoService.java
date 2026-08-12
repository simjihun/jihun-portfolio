package com.jihun.portfolio.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * EC2 인스턴스 메타데이터 조회 (IMDSv2).
 * 인스턴스 자신에게 물어보는 로컬 전용 API(169.254.169.254)라 별도 AWS 액세스키가 필요 없다 —
 * 이 서비스가 EC2 안에서 돌고 있을 때만 응답하고, 로컬 개발 환경이나 EC2가 아닌 곳에서는
 * 짧은 타임아웃(800ms) 뒤 실패로 처리된다.
 *
 * 사용량·과금·프리티어 잔여일수 같은 정보는 별도 AWS Cost Explorer/Billing API가 필요하고
 * 그건 이 인스턴스 메타데이터와 달리 진짜 AWS 액세스키(IAM 자격증명)를 서버에 저장해야 해서
 * 보안 영향 범위가 다르다 — 현재는 미구현이며, 붙이려면 최소 권한(billing 조회 전용) IAM
 * 사용자를 새로 만들어 conf/private.conf에 추가해야 한다.
 */
@Service
public class AwsInstanceInfoService {

    private static final Logger log = LoggerFactory.getLogger(AwsInstanceInfoService.class);
    private static final String IMDS_BASE = "http://169.254.169.254/latest";

    private final RestTemplate rest;

    public AwsInstanceInfoService() {
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(800);
        f.setReadTimeout(800);
        this.rest = new RestTemplate(f);
    }

    public Map<String, Object> getInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        try {
            String token = fetchToken();
            result.put("available", true);
            result.put("instanceId", fetchMeta(token, "instance-id"));
            result.put("instanceType", fetchMeta(token, "instance-type"));
            result.put("availabilityZone", fetchMeta(token, "placement/availability-zone"));
            result.put("region", fetchMeta(token, "placement/region"));
            result.put("privateIp", fetchMeta(token, "local-ipv4"));
            result.put("publicIp", fetchMetaSafe(token, "public-ipv4"));
            result.put("amiId", fetchMeta(token, "ami-id"));
        } catch (Exception e) {
            log.info("[admin-dashboard] EC2 메타데이터 조회 불가(EC2 외부 환경일 수 있음): {}", e.getMessage());
            result.put("available", false);
            result.put("message", "EC2 인스턴스 메타데이터를 가져오지 못했습니다 (로컬 개발 환경이거나 EC2 외부에서 실행 중일 수 있음)");
        }
        result.put("usageNote", "사용량·과금·프리티어 잔여일수는 별도 AWS Billing API 연동이 필요해 아직 표시하지 않습니다.");
        return result;
    }

    private String fetchToken() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-aws-ec2-metadata-token-ttl-seconds", "21600");
        ResponseEntity<String> res = rest.exchange(IMDS_BASE + "/api/token", HttpMethod.PUT, new HttpEntity<>(headers), String.class);
        return res.getBody();
    }

    private String fetchMeta(String token, String path) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-aws-ec2-metadata-token", token);
        ResponseEntity<String> res = rest.exchange(IMDS_BASE + "/meta-data/" + path, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        return res.getBody();
    }

    private String fetchMetaSafe(String token, String path) {
        try {
            return fetchMeta(token, path);
        } catch (Exception e) {
            return null; // 퍼블릭 IP가 없는 인스턴스(사설 서브넷 등)일 수 있어 여기만 실패를 허용
        }
    }
}
