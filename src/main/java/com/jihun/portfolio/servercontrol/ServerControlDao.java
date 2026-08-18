package com.jihun.portfolio.servercontrol;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collections;
import java.util.List;

/**
 * 서버제어 DAO — MyBatis(iBatis) Mapper XML이나 JPA Repository 없이,
 * JdbcTemplate에 SQL을 직접 박아 넣는 방식이다. 10년 전 프로젝트 상당수가
 * (Mapper XML을 쓰지 않는 경우) 정확히 이런 모습이었다.
 */
@Repository
public class ServerControlDao {

    @Autowired
    private JdbcTemplate jdbcTemplate;

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

    // ===== DB CRUD 연습용 더미 테이블 =====

    public List<PracticeRecordVo> selectDummyList() {
        String sql = "SELECT record_id, record_name, record_email, record_status, reg_date, upd_date "
                   + "FROM practice_dummy_table ORDER BY record_id DESC";
        return jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(PracticeRecordVo.class));
    }

    public int insertDummyRecord(PracticeRecordVo vo) {
        String sql = "INSERT INTO practice_dummy_table (record_name, record_email, record_status) VALUES (?, ?, ?)";
        return jdbcTemplate.update(sql, vo.getRecordName(), vo.getRecordEmail(), vo.getRecordStatus());
    }

    public int updateDummyRecord(PracticeRecordVo vo) {
        String sql = "UPDATE practice_dummy_table SET record_name = ?, record_email = ?, record_status = ?, upd_date = NOW() "
                   + "WHERE record_id = ?";
        return jdbcTemplate.update(sql, vo.getRecordName(), vo.getRecordEmail(), vo.getRecordStatus(), vo.getRecordId());
    }

    public int deleteDummyRecord(Long recordId) {
        String sql = "DELETE FROM practice_dummy_table WHERE record_id = ?";
        return jdbcTemplate.update(sql, recordId);
    }
}
