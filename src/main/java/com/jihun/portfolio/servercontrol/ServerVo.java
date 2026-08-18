package com.jihun.portfolio.servercontrol;

import java.time.LocalDateTime;

/**
 * 서버 마스터 정보 VO.
 * BeanPropertyRowMapper가 컬럼명(snake_case)을 프로퍼티명(camelCase)으로 자동
 * 매핑하므로, DAO에서 SELECT하는 컬럼명과 아래 필드명이 1:1로 대응되어야 한다.
 */
public class ServerVo {

    private Long serverId;
    private String serverName;
    private String serverAlias;
    private String hostIp;
    private Integer sshPort;
    private String osType;
    private String envType;
    private String status; // RUNNING, STOPPED
    private LocalDateTime regDate;

    public Long getServerId() {
        return serverId;
    }

    public void setServerId(Long serverId) {
        this.serverId = serverId;
    }

    public String getServerName() {
        return serverName;
    }

    public void setServerName(String serverName) {
        this.serverName = serverName;
    }

    public String getServerAlias() {
        return serverAlias;
    }

    public void setServerAlias(String serverAlias) {
        this.serverAlias = serverAlias;
    }

    public String getHostIp() {
        return hostIp;
    }

    public void setHostIp(String hostIp) {
        this.hostIp = hostIp;
    }

    public Integer getSshPort() {
        return sshPort;
    }

    public void setSshPort(Integer sshPort) {
        this.sshPort = sshPort;
    }

    public String getOsType() {
        return osType;
    }

    public void setOsType(String osType) {
        this.osType = osType;
    }

    public String getEnvType() {
        return envType;
    }

    public void setEnvType(String envType) {
        this.envType = envType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getRegDate() {
        return regDate;
    }

    public void setRegDate(LocalDateTime regDate) {
        this.regDate = regDate;
    }
}
