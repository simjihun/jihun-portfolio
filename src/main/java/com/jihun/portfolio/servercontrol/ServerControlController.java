package com.jihun.portfolio.servercontrol;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.List;

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

    @RequestMapping(value = "/query", method = RequestMethod.POST)
    @ResponseBody
    public ResultVo query(@RequestParam("sql") String sql) {
        return serverControlService.executeQuery(sql);
    }

    // ===== INSERT/UPDATE 연습 전용 테이블 =====

    @RequestMapping(value = "/template/list", method = RequestMethod.GET)
    @ResponseBody
    public ResultVo templateList() {
        return ResultVo.success(serverControlService.getTemplateList());
    }

    @RequestMapping(value = "/template/insert", method = RequestMethod.POST)
    @ResponseBody
    public ResultVo templateInsert(PracticeTemplateVo vo) {
        return serverControlService.insertTemplate(vo);
    }

    @RequestMapping(value = "/template/update", method = RequestMethod.POST)
    @ResponseBody
    public ResultVo templateUpdate(PracticeTemplateVo vo) {
        return serverControlService.updateTemplate(vo);
    }

    @RequestMapping(value = "/schedule/list", method = RequestMethod.GET)
    @ResponseBody
    public ResultVo scheduleList() {
        return ResultVo.success(serverControlService.getScheduleList());
    }

    @RequestMapping(value = "/schedule/insert", method = RequestMethod.POST)
    @ResponseBody
    public ResultVo scheduleInsert(PracticeScheduleVo vo) {
        return serverControlService.insertSchedule(vo);
    }

    @RequestMapping(value = "/schedule/update", method = RequestMethod.POST)
    @ResponseBody
    public ResultVo scheduleUpdate(PracticeScheduleVo vo) {
        return serverControlService.updateSchedule(vo);
    }
}
