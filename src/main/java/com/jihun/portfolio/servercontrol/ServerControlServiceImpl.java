package com.jihun.portfolio.servercontrol;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ServerControlServiceImpl implements ServerControlService {

    @Autowired
    private ServerControlDao serverControlDao;

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

    /**
     * 실제 서버에 SSH로 접속해 systemctl start/stop을 실행하는 대신, DB의 상태값만
     * 바꾸는 시뮬레이션이다. 실무에서 여러 서버를 HTTP로 제어하려면 각 서버에 작은
     * 에이전트(Node.js나 Spring Boot로 만든 API 서버)를 상주시키고, 이 컨트롤러가
     * 그 에이전트의 "/control?action=restart" 같은 엔드포인트를 RestTemplate/
     * WebClient로 호출하는 구조가 된다. 지금은 그 통신 규격이 없으니 DB 갱신으로
     * 대체했다.
     */
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

    @Override
    public List<PracticeRecordVo> getDummyList() {
        return serverControlDao.selectDummyList();
    }

    @Override
    public ResultVo insertDummy(PracticeRecordVo vo) {
        if (vo.getRecordName() == null || vo.getRecordName().trim().length() == 0) {
            return ResultVo.fail("이름은 필수입니다.");
        }
        if (vo.getRecordStatus() == null || vo.getRecordStatus().trim().length() == 0) {
            vo.setRecordStatus("ACTIVE");
        }
        serverControlDao.insertDummyRecord(vo);
        return ResultVo.success();
    }

    @Override
    public ResultVo updateDummy(PracticeRecordVo vo) {
        if (vo.getRecordId() == null) {
            return ResultVo.fail("수정할 레코드를 찾을 수 없습니다.");
        }
        if (vo.getRecordName() == null || vo.getRecordName().trim().length() == 0) {
            return ResultVo.fail("이름은 필수입니다.");
        }
        serverControlDao.updateDummyRecord(vo);
        return ResultVo.success();
    }

    @Override
    public ResultVo deleteDummy(Long recordId) {
        if (recordId == null) {
            return ResultVo.fail("삭제할 레코드를 찾을 수 없습니다.");
        }
        serverControlDao.deleteDummyRecord(recordId);
        return ResultVo.success();
    }
}
