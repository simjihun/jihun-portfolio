package com.jihun.portfolio.servercontrol;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class ServerControlServiceImpl implements ServerControlService {

    @Autowired
    private ServerControlDao serverControlDao;

    // ===== SQL 콘솔 화이트리스트 =====
    private static final Set<String> ALLOWED_TABLES = Set.of("mms_practice_message_log", "mms_practice_server_config");
    private static final int MAX_SQL_LENGTH = 1000;
    private static final int MAX_RESULT_ROWS = 200;

    private static final Pattern FORBIDDEN_KEYWORD_PATTERN = Pattern.compile(
        "\\b(insert|update|delete|drop|alter|truncate|grant|revoke|create|replace|exec|execute|call|"
        + "into\\s+outfile|into\\s+dumpfile|load_file|information_schema|sleep|benchmark)\\b",
        Pattern.CASE_INSENSITIVE);

    private static final Pattern TABLE_REF_PATTERN = Pattern.compile(
        "\\b(?:from|join)\\s+`?([a-zA-Z_][a-zA-Z0-9_]*)`?", Pattern.CASE_INSENSITIVE);

    @Override
    public List<ServerVo> getServerList() {
        return serverControlDao.selectServerList();
    }

    @Override
    public ServerVo getServerDetail(Long serverId) {
        return serverControlDao.selectServerById(serverId);
    }

    @Override
    public List<ServerControlLogVo> getControlLogs(Long serverId) {
        return serverControlDao.selectControlLogByServerId(serverId);
    }

    @Override
    public ResultVo controlServer(Long serverId, String actionType) {
        ServerVo server = serverControlDao.selectServerById(serverId);
        if (server == null) {
            return ResultVo.fail("존재하지 않는 서버입니다.");
        }

        String currentStatus = server.getStatus();
        String nextStatus;

        if ("START".equals(actionType)) {
            if ("RUNNING".equals(currentStatus)) {
                return ResultVo.fail(server.getServerAlias() + "는 이미 실행 중입니다.");
            }
            nextStatus = "RUNNING";
        } else if ("STOP".equals(actionType)) {
            if ("STOPPED".equals(currentStatus)) {
                return ResultVo.fail(server.getServerAlias() + "는 이미 중지 상태입니다.");
            }
            nextStatus = "STOPPED";
        } else if ("RESTART".equals(actionType)) {
            nextStatus = "RUNNING";
        } else {
            return ResultVo.fail("알 수 없는 제어 명령입니다: " + actionType);
        }

        serverControlDao.updateServerStatus(serverId, nextStatus);

        ServerControlLogVo logVo = new ServerControlLogVo();
        logVo.setServerId(serverId);
        logVo.setActionType(actionType);
        logVo.setActionResult("SUCCESS");
        logVo.setRequestedBy("admin");
        serverControlDao.insertControlLog(logVo);

        return ResultVo.success(nextStatus);
    }

    @Override
    public List<ServerMetricVo> getMetricHistory(Long serverId) {
        return serverControlDao.selectRecentMetrics(serverId, 30);
    }

    /**
     * SQL 콘솔 실행 진입점. 아래 검증을 순서대로 통과한 쿼리만 실제로 실행한다.
     *   1) 길이 제한
     *   2) 세미콜론으로 여러 문장을 이어붙였는지(끝의 세미콜론 1개는 허용)
     *   3) 주석 구문(--, #, /* ) 포함 여부
     *   4) SELECT로 시작하는지
     *   5) 금지 키워드(쓰기·DDL·시스템 함수 계열) 포함 여부
     *   6) FROM/JOIN 대상 테이블이 허용 목록에만 속하는지
     */
    @Override
    public ResultVo executeQuery(String sqlInput) {
        if (sqlInput == null) {
            return ResultVo.fail("쿼리를 입력해주세요.");
        }
        String sql = sqlInput.trim();
        if (sql.isEmpty()) {
            return ResultVo.fail("쿼리를 입력해주세요.");
        }
        if (sql.length() > MAX_SQL_LENGTH) {
            return ResultVo.fail("쿼리가 너무 깁니다(최대 " + MAX_SQL_LENGTH + "자).");
        }

        // 끝의 세미콜론 1개는 잘라내고, 그 뒤에 다른 문장이 이어지는지 확인
        String normalized = sql;
        if (normalized.endsWith(";")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.contains(";")) {
            return ResultVo.fail("한 번에 하나의 SELECT 문만 실행할 수 있습니다.");
        }

        if (normalized.contains("--") || normalized.contains("#") || normalized.contains("/*")) {
            return ResultVo.fail("주석 구문은 사용할 수 없습니다.");
        }

        String lower = normalized.trim().toLowerCase();
        if (!lower.startsWith("select")) {
            return ResultVo.fail("SELECT 문만 실행할 수 있습니다.");
        }

        Matcher forbidden = FORBIDDEN_KEYWORD_PATTERN.matcher(normalized);
        if (forbidden.find()) {
            return ResultVo.fail("허용되지 않는 키워드가 포함되어 있습니다: " + forbidden.group());
        }

        List<String> referencedTables = new ArrayList<>();
        Matcher tableMatcher = TABLE_REF_PATTERN.matcher(normalized);
        while (tableMatcher.find()) {
            referencedTables.add(tableMatcher.group(1).toLowerCase());
        }
        if (referencedTables.isEmpty()) {
            return ResultVo.fail("FROM 절이 필요합니다.");
        }
        for (String table : referencedTables) {
            if (!ALLOWED_TABLES.contains(table)) {
                return ResultVo.fail("허용된 테이블만 조회할 수 있습니다: " + String.join(", ", ALLOWED_TABLES));
            }
        }

        try {
            List<Map<String, Object>> rows = serverControlDao.executeSelect(normalized);
            boolean truncated = rows.size() > MAX_RESULT_ROWS;
            List<Map<String, Object>> limited = truncated ? rows.subList(0, MAX_RESULT_ROWS) : rows;

            List<String> columns = new ArrayList<>();
            if (!limited.isEmpty()) {
                columns.addAll(limited.get(0).keySet());
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("columns", columns);
            payload.put("rows", limited);
            payload.put("truncated", truncated);
            payload.put("totalRows", rows.size());
            return ResultVo.success(payload);
        } catch (Exception e) {
            return ResultVo.fail("쿼리 실행 중 오류가 발생했습니다: " + e.getMessage());
        }
    }
}
