package com.jihun.portfolio.servercontrol;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

/**
 * 서버제어 연습용 컨트롤러.
 *
 * 요즘 흔한 @RestController + @GetMapping/@PostMapping 대신, 10년 전 Spring MVC에서
 * 자주 보이던 @Controller 클래스 + 메서드마다 @ResponseBody, @RequestMapping(value=...,
 * method=RequestMethod.xxx) 조합을 그대로 썼다. 옛날 프로젝트를 맡았을 때 이 스타일의
 * 코드를 읽고 고치는 데 익숙해지기 위한 연습.
 */
@Controller
@RequestMapping("/api/servercontrol")
public class ServerControlController {

    @Autowired
    private ServerControlService serverControlService;

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public ResultVo serverList() {
        List<ServerVo> list = serverControlService.getServerList();
        return ResultVo.success(list);
    }

    @RequestMapping(value = "/detail", method = RequestMethod.GET)
    @ResponseBody
    public ResultVo serverDetail(@RequestParam("serverId") Long serverId) {
        ServerVo vo = serverControlService.getServerDetail(serverId);
        if (vo == null) {
            return ResultVo.fail("서버 정보를 찾을 수 없습니다.");
        }
        return ResultVo.success(vo);
    }

    @RequestMapping(value = "/control", method = RequestMethod.POST)
    @ResponseBody
    public ResultVo control(@RequestParam("serverId") Long serverId,
                             @RequestParam("actionType") String actionType) {
        return serverControlService.controlServer(serverId, actionType);
    }

    @RequestMapping(value = "/logs", method = RequestMethod.GET)
    @ResponseBody
    public ResultVo controlLogs(@RequestParam("serverId") Long serverId) {
        List<ServerControlLogVo> logs = serverControlService.getControlLogs(serverId);
        return ResultVo.success(logs);
    }

    @RequestMapping(value = "/metrics", method = RequestMethod.GET)
    @ResponseBody
    public ResultVo metrics(@RequestParam("serverId") Long serverId) {
        List<ServerMetricVo> list = serverControlService.getMetricHistory(serverId);
        return ResultVo.success(list);
    }

    // ===== DB CRUD 연습 (더미 테이블) =====

    @RequestMapping(value = "/dummy/list", method = RequestMethod.GET)
    @ResponseBody
    public ResultVo dummyList() {
        List<PracticeRecordVo> list = serverControlService.getDummyList();
        return ResultVo.success(list);
    }

    @RequestMapping(value = "/dummy/insert", method = RequestMethod.POST)
    @ResponseBody
    public ResultVo dummyInsert(PracticeRecordVo vo) {
        return serverControlService.insertDummy(vo);
    }

    @RequestMapping(value = "/dummy/update", method = RequestMethod.POST)
    @ResponseBody
    public ResultVo dummyUpdate(PracticeRecordVo vo) {
        return serverControlService.updateDummy(vo);
    }

    @RequestMapping(value = "/dummy/delete", method = RequestMethod.POST)
    @ResponseBody
    public ResultVo dummyDelete(@RequestParam("recordId") Long recordId) {
        return serverControlService.deleteDummy(recordId);
    }
}
