package com.jihun.portfolio.servercontrol;

import java.util.List;

public interface ServerControlService {

    List<ServerVo> getServerList();

    ServerVo getServerDetail(Long serverId);

    ResultVo controlServer(Long serverId, String actionType);

    List<ServerControlLogVo> getControlLogs(Long serverId);

    List<ServerMetricVo> getMetricHistory(Long serverId);

    ResultVo executeQuery(String sql);

    List<PracticeTemplateVo> getTemplateList();

    ResultVo insertTemplate(PracticeTemplateVo vo);

    ResultVo updateTemplate(PracticeTemplateVo vo);

    List<PracticeScheduleVo> getScheduleList();

    ResultVo insertSchedule(PracticeScheduleVo vo);

    ResultVo updateSchedule(PracticeScheduleVo vo);
}
