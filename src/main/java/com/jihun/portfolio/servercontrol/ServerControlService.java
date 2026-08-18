package com.jihun.portfolio.servercontrol;

import java.util.List;

/**
 * 서비스 인터페이스 + 구현체 분리는 10년 전 Spring 프로젝트의 정석 패턴이었다.
 * (지금은 구현체가 하나뿐이면 인터페이스를 생략하는 경우가 많지만, 당시엔
 * 테스트용 Mock 구현체를 끼워 넣기 쉽게 하려고 관례적으로 항상 분리했다.)
 */
public interface ServerControlService {

    List<ServerVo> getServerList();

    ServerVo getServerDetail(Long serverId);

    ResultVo controlServer(Long serverId, String actionType);

    List<ServerControlLogVo> getControlLogs(Long serverId);

    List<ServerMetricVo> getMetricHistory(Long serverId);

    List<PracticeRecordVo> getDummyList();

    ResultVo insertDummy(PracticeRecordVo vo);

    ResultVo updateDummy(PracticeRecordVo vo);

    ResultVo deleteDummy(Long recordId);
}
