package com.jihun.portfolio.servercontrol;

/**
 * 옛날 스타일 공통 응답 VO.
 * 최근에는 ResponseEntity + 표준 HTTP 상태코드를 많이 쓰지만, 10년 전 다수의 한국
 * 기업 프로젝트는 이런 식의 {"resultCode":"0000","resultMsg":"...","data":...}
 * 자체 규격을 응답 바디에 담아 200 OK로 내려주는 경우가 흔했다. 그 감각을 그대로
 * 연습하기 위해 이 VO를 둔다.
 */
public class ResultVo {

    public static final String SUCCESS_CODE = "0000";
    public static final String FAIL_CODE = "9999";

    private String resultCode;
    private String resultMsg;
    private Object data;

    public ResultVo() {
    }

    public ResultVo(String resultCode, String resultMsg) {
        this.resultCode = resultCode;
        this.resultMsg = resultMsg;
    }

    public ResultVo(String resultCode, String resultMsg, Object data) {
        this.resultCode = resultCode;
        this.resultMsg = resultMsg;
        this.data = data;
    }

    public static ResultVo success(Object data) {
        return new ResultVo(SUCCESS_CODE, "정상 처리되었습니다.", data);
    }

    public static ResultVo success() {
        return new ResultVo(SUCCESS_CODE, "정상 처리되었습니다.");
    }

    public static ResultVo fail(String msg) {
        return new ResultVo(FAIL_CODE, msg);
    }

    public String getResultCode() {
        return resultCode;
    }

    public void setResultCode(String resultCode) {
        this.resultCode = resultCode;
    }

    public String getResultMsg() {
        return resultMsg;
    }

    public void setResultMsg(String resultMsg) {
        this.resultMsg = resultMsg;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }
}
