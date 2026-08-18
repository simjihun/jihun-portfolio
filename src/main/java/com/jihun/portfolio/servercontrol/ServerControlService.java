package com.jihun.portfolio.servercontrol;

import java.util.List;

public interface ServerControlService {

    List<ServerVo> getServerList();

    ServerVo getServerDetail(Long serverId);

    ResultVo controlServer(Long serverId, String actionType);

    List<ServerControlLogVo> getControlLogs(Long serverId);

    List<ServerMetricVo> getMetricHistory(Long serverId);

    ResultVo executeQuery(String sql);
}
