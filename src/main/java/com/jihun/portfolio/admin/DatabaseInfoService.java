package com.jihun.portfolio.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * DB 연결 정보·테이블 사용량 조회. 앱이 이미 쓰고 있는 DataSource로 조회하는 방식이라 새
 * 자격증명이 필요 없다(운영 DB 계정에 기본으로 포함된 조회 권한만 사용). MySQL 기준으로 작성했고,
 * 로컬 H2 환경에서는 일부 컬럼이 달라 실패할 수 있어 예외 발생 시 안내 메시지만 반환한다.
 *
 * 행 수는 information_schema.tables.TABLE_ROWS(추정치)를 쓰지 않고 테이블마다 SELECT COUNT(*)를
 * 직접 실행해 정확히 센다 — InnoDB의 TABLE_ROWS는 백그라운드 통계 갱신 주기에 따라 갱신되는
 * 추정치라, 데이터를 방금 넣었거나 테이블이 작으면 실제로는 행이 있어도 0으로 보이는 경우가
 * 흔하다. 용량(size_mb)은 정확한 값을 얻기 훨씬 비싸서(테이블 전체를 훑어야 함) 계속
 * information_schema의 추정치를 쓴다 — 대시보드 용도로는 이 정도 정밀도로 충분하다.
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

            // 1) 용량은 information_schema 추정치, 테이블 이름 목록도 여기서 함께 얻는다
            String sizeSql = "SELECT table_name AS t_name, "
                    + "ROUND((data_length + index_length) / 1024 / 1024, 2) AS size_mb "
                    + "FROM information_schema.tables WHERE table_schema = ? "
                    + "ORDER BY (data_length + index_length) DESC";
            try (PreparedStatement ps = conn.prepareStatement(sizeSql)) {
                ps.setString(1, dbName);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        Map<String, Object> t = new LinkedHashMap<>();
                        t.put("name", rs.getString("t_name"));
                        t.put("sizeMb", rs.getDouble("size_mb"));
                        tables.add(t);
                    }
                }
            }

            // 2) 행 수는 테이블마다 정확히 센다(COUNT(*)) — 테이블 개수가 적은 개인 사이트라 부담 없음
            try (Statement st = conn.createStatement()) {
                for (Map<String, Object> t : tables) {
                    String tableName = (String) t.get("name");
                    try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM `" + tableName + "`")) {
                        t.put("rows", rs.next() ? rs.getLong(1) : 0L);
                    } catch (Exception e) {
                        t.put("rows", null); // 권한 문제 등으로 특정 테이블만 실패해도 나머지는 계속 보여줌
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
