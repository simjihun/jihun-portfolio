package com.jihun.portfolio.servercontrol;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@Repository
public class ServerControlDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @PostConstruct
    public void init() {
        jdbcTemplate.setQueryTimeout(5); // SQL 콘솔에서 실행되는 쿼리는 5초로 제한
    }

    // ===== 서버 마스터 =====

    public List<ServerVo> selectServerList() {
        String sql = "SELECT server_id, server_name, server_alias, host_ip, ssh_port, os_type, env_type, status, reg_date "
                   + "FROM admin_servers ORDER BY server_id ASC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ServerVo.class));
    }

    public ServerVo selectServerById(Long serverId) {
        String sql = "SELECT server_id, server_name, server_alias, host_ip, ssh_port, os_type, env_type, status, reg_date "
                   + "FROM admin_servers WHERE server_id = ?";
        List<ServerVo> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ServerVo.class), serverId);
        return list.isEmpty() ? null : list.get(0);
    }

    public int updateServerStatus(Long serverId, String status) {
        String sql = "UPDATE admin_servers SET status = ? WHERE server_id = ?";
        return jdbcTemplate.update(sql, status, serverId);
    }

    // ===== 제어 이력 =====

    public void insertControlLog(ServerControlLogVo vo) {
        String sql = "INSERT INTO admin_server_control_log (server_id, action_type, action_result, requested_by) "
                   + "VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, vo.getServerId(), vo.getActionType(), vo.getActionResult(), vo.getRequestedBy());
    }

    public List<ServerControlLogVo> selectControlLogByServerId(Long serverId) {
        String sql = "SELECT log_id, server_id, action_type, action_result, requested_by, requested_at "
                   + "FROM admin_server_control_log WHERE server_id = ? ORDER BY requested_at DESC LIMIT 20";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ServerControlLogVo.class), serverId);
    }

    // ===== 리소스 지표 =====

    public List<ServerMetricVo> selectRecentMetrics(Long serverId, int limit) {
        String sql = "SELECT metric_id, server_id, cpu_usage, mem_usage, disk_usage, checked_at "
                   + "FROM admin_server_metric WHERE server_id = ? ORDER BY checked_at DESC LIMIT ?";
        List<ServerMetricVo> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ServerMetricVo.class), serverId, limit);
        Collections.reverse(list); // 최신순으로 가져온 걸 시간순으로 되돌려 차트에 바로 사용
        return list;
    }

    public ServerMetricVo selectLatestMetric(Long serverId) {
        String sql = "SELECT metric_id, server_id, cpu_usage, mem_usage, disk_usage, checked_at "
                   + "FROM admin_server_metric WHERE server_id = ? ORDER BY checked_at DESC LIMIT 1";
        List<ServerMetricVo> list = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(ServerMetricVo.class), serverId);
        return list.isEmpty() ? null : list.get(0);
    }

    public void insertMetric(ServerMetricVo vo) {
        String sql = "INSERT INTO admin_server_metric (server_id, cpu_usage, mem_usage, disk_usage) VALUES (?, ?, ?, ?)";
        jdbcTemplate.update(sql, vo.getServerId(), vo.getCpuUsage(), vo.getMemUsage(), vo.getDiskUsage());
    }

    // ===== SQL 콘솔 (화이트리스트 검증은 ServerControlServiceImpl에서 수행 후 호출) =====

    /**
     * 검증이 끝난 SELECT 쿼리를 그대로 실행해 컬럼명을 키로 하는 Map 리스트로 돌려준다.
     * 쿼리 자체의 안전성 검증은 여기서 하지 않는다 — 반드시 Service 계층의 화이트리스트
     * 검사를 통과한 뒤에만 이 메서드를 호출해야 한다.
     */
    public List<Map<String, Object>> executeSelect(String sql) {
        return jdbcTemplate.queryForList(sql);
    }

    // ===== INSERT/UPDATE 연습 전용 테이블 (모달 폼을 통해서만 값이 들어온다) =====

    public List<PracticeTemplateVo> selectTemplateList() {
        String sql = "SELECT template_id, server_id, template_name, template_content, use_yn, reg_date, upd_date "
                   + "FROM mms_practice_template ORDER BY template_id DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(PracticeTemplateVo.class));
    }

    public int insertTemplate(PracticeTemplateVo vo) {
        String sql = "INSERT INTO mms_practice_template (server_id, template_name, template_content, use_yn) VALUES (?, ?, ?, ?)";
        return jdbcTemplate.update(sql, vo.getServerId(), vo.getTemplateName(), vo.getTemplateContent(), vo.getUseYn());
    }

    public int updateTemplate(PracticeTemplateVo vo) {
        String sql = "UPDATE mms_practice_template SET server_id = ?, template_name = ?, template_content = ?, use_yn = ?, upd_date = NOW() "
                   + "WHERE template_id = ?";
        return jdbcTemplate.update(sql, vo.getServerId(), vo.getTemplateName(), vo.getTemplateContent(), vo.getUseYn(), vo.getTemplateId());
    }

    public List<PracticeScheduleVo> selectScheduleList() {
        String sql = "SELECT schedule_id, server_id, schedule_name, send_time, repeat_type, status, reg_date, upd_date "
                   + "FROM mms_practice_schedule ORDER BY schedule_id DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(PracticeScheduleVo.class));
    }

    public int insertSchedule(PracticeScheduleVo vo) {
        String sql = "INSERT INTO mms_practice_schedule (server_id, schedule_name, send_time, repeat_type, status) VALUES (?, ?, ?, ?, ?)";
        return jdbcTemplate.update(sql, vo.getServerId(), vo.getScheduleName(), vo.getSendTime(), vo.getRepeatType(), vo.getStatus());
    }

    public int updateSchedule(PracticeScheduleVo vo) {
        String sql = "UPDATE mms_practice_schedule SET server_id = ?, schedule_name = ?, send_time = ?, repeat_type = ?, status = ?, upd_date = NOW() "
                   + "WHERE schedule_id = ?";
        return jdbcTemplate.update(sql, vo.getServerId(), vo.getScheduleName(), vo.getSendTime(), vo.getRepeatType(), vo.getStatus(), vo.getScheduleId());
    }
}
