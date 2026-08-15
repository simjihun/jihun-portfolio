package com.jihun.portfolio.common;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 깔끔한 URL 매핑 설정.
 * 확장자(.html) 없는 주소를 실제 정적 파일로 포워딩한다.
 * 새 기능 등록 시 여기에 한 줄만 추가하면 됨.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 메시징
        registry.addViewController("/mms").setViewName("forward:/mms.html");
        // 뉴스
        registry.addViewController("/news").setViewName("forward:/news.html");
        // 지도 — 맛집/부동산을 하나의 통합 페이지에서 토글로 전환해 보여준다
        registry.addViewController("/map").setViewName("forward:/map.html");
        registry.addViewController("/map/food").setViewName("forward:/map.html");
        registry.addViewController("/map/estate").setViewName("forward:/map.html");
        // 웹 도구 — 개발자용 미니툴 모음(JSON 포매터, 정규식 테스터 등). 브라우저에서만 처리하고
        // 서버 API가 필요 없어 별도 컨트롤러 없이 정적 페이지만 포워딩한다. 도구가 30~50개로 늘어나도
        // 이 와일드카드 한 줄이면 충분 — 어떤 도구를 보여줄지는 webtool.html의 JS가 URL 뒷부분(슬러그,
        // 예: /webtool/json-formatter)을 읽어서 처리한다.
        // 현재 메뉴에는 웹 에디터 개발 착수로 잠시 숨겨져 있지만(nav.js), 라우팅은 유지한다.
        registry.addViewController("/webtool").setViewName("forward:/webtool.html");
        registry.addViewController("/webtool/**").setViewName("forward:/webtool.html");
        // 웹 에디터 — Monaco Editor 기반 HTML/CSS/JS·Vue·React 실습 및 공유(CodePen 스타일).
        // 스니펫 상세는 /api/webeditor/snippets/{id} REST API로 조회하고, 어떤 스니펫을 보여줄지는
        // editor.html의 JS가 URL 뒷부분(슬러그, 예: /editor/42)을 읽어서 처리한다.
        registry.addViewController("/editor").setViewName("forward:/editor.html");
        registry.addViewController("/editor/**").setViewName("forward:/editor.html");
        // 웹 게임(상위 분류) — /game 아래 웹게임(숫자야구·발리볼) / board(오목·장기) / card(프리셀·클론다이크·스파이더) 3갈래
        registry.addViewController("/game").setViewName("forward:/game.html");
        registry.addViewController("/game/baseball").setViewName("forward:/game.html");
        registry.addViewController("/game/volleyball").setViewName("forward:/game.html");
        registry.addViewController("/game/board").setViewName("forward:/board.html");
        registry.addViewController("/game/board/omok").setViewName("forward:/board.html");
        registry.addViewController("/game/board/janggi").setViewName("forward:/board.html");
        registry.addViewController("/game/card").setViewName("forward:/cardgame.html");
        registry.addViewController("/game/card/freecell").setViewName("forward:/cardgame.html");
        registry.addViewController("/game/card/klondike").setViewName("forward:/cardgame.html");
        registry.addViewController("/game/card/spider").setViewName("forward:/cardgame.html");
        // 주식 AI 대시보드
        registry.addViewController("/stock").setViewName("forward:/stock.html");

        // 비공개 회원 영역 — 공개 포트폴리오 nav에는 노출하지 않음(직접 URL로만 접근)
        registry.addViewController("/login").setViewName("forward:/login.html");
        registry.addViewController("/signup").setViewName("forward:/signup.html");
        registry.addViewController("/mypage").setViewName("forward:/mypage.html");              // 로그인 후 기본 랜딩 = 대시보드
        registry.addViewController("/mypage/settings").setViewName("forward:/mypage-settings.html"); // 비밀번호 변경 등 계정 설정
        // 관리자 허브 + 하위 기능. 새 관리자 전용 기능을 추가할 때마다 /admin/<기능>[/<세부기능>]
        // 형태로 한 줄씩 추가한다 (예: /admin/instaviewer, /admin/instaviewer/photos 등).
        registry.addViewController("/admin").setViewName("forward:/admin.html");
        registry.addViewController("/admin/members").setViewName("forward:/admin-members.html");
        registry.addViewController("/admin/toss-stock").setViewName("forward:/toss-stock.html"); // TOSS 주식(개인 보유종목·조회 전용)
    }
}
