package com.jihun.portfolio.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DB 연결 정보·테이블 사용량 조회. 앱이 이미 쓰고 있는 DataSource로 information_schema를
 * 조회하는 방식이라 새 자격증명이 필요 없다(운영 DB 계정에 information_schema 조회 권한은
 * 기본으로 포함돼 있음). MySQL 기준으로 작성했고, 로컬 H2 환경에서는 일부 컬럼이 달라 실패할 수
 * 있어 예외 발생 시 안내 메시지만 반환한다.
 */
@Service
public class DatabaseInfoService {

    private static final Pattern HOST_PATTERN = Pattern.compile("jdbc:mysql://([^:/]+)(?::(\\d+))?/([^?]+)");

    private final DataSource dataSource;

    @Value("${spring.datasource.url}")
    private String jdbcUrl;

    public DatabaseInfoService(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Map<String, Object> getInfo() {
        Map<String, Object> result = new LinkedHashMap<>();
        Matcher m = HOST_PATTERN.matcher(jdbcUrl);
        if (m.find()) {
            result.put("host", m.group(1));
            result.put("port", m.group(2) != null ? m.group(2) : "3306");
            result.put("database", m.group(3));
        } else {
            result.put("host", "-");
            result.put("database", jdbcUrl.contains("h2:mem") ? "H2(로컬 인메모리)" : jdbcUrl);
        }

        List<Map<String, Object>> tables = new ArrayList<>();
        try (Connection conn = dataSource.getConnection()) {
            String dbName = conn.getCatalog();
            String sql = "SELECT table_name AS t_name, table_rows AS t_rows, "
                    + "ROUND((data_length + index_length) / 1024 / 1024, 2) AS size_mb "
                    + "FROM information_schema.tables WHERE table_schema = ? "
                    + "ORDER BY (data_length + index_length) DESC";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, dbName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("name", rs.getString("t_name"));
                        t.put("rows", rs.getLong("t_rows"));
                        t.put("sizeMb", rs.getDouble("size_mb"));
                        tables.add(t);
                    }
                }
            }
            result.put("connected", true);
        } catch (Exception e) {
            result.put("connected", false);
            result.put("message", "테이블 사용량 조회에 실패했습니다 (" + e.getMessage() + ") — H2 로컬 환경이거나 권한 문제일 수 있습니다");
        }
        result.put("tables", tables);
        return result;
    }
}
