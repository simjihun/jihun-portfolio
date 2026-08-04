# CLAUDE.md — hunit-portfolio 프로젝트 핸드오프 문서

Claude Code(또는 다른 Claude 세션)가 이 레포를 처음 열었을 때 빠르게 맥락을 잡기 위한 문서입니다.
사람이 읽어도 되고, Claude가 세션 시작 시 참고해도 됩니다.

## 사이트 개요

- **사이트**: https://hunit.kr
- **스택**: Java 21, Spring Boot 3.5, JPA, MySQL(RDS)/H2(로컬), Vanilla JS + Chart.js
- **인프라**: AWS EC2(Amazon Linux 2023) + nginx 리버스 프록시 + Let's Encrypt HTTPS + GitHub Actions CI/CD
- **배포**: `main` 브랜치에 push하면 자동 빌드·배포됨 (`.github/workflows/`)
- **로컬 실행**: `mvn spring-boot:run` (포트 8081, H2 인메모리)

## 기능별 현황 (2026-08-04 기준)

| 기능 | 상태 | 비고 |
|---|---|---|
| MMS (`/mms`) | LIVE | 워커 5개, 다중발송 1000건, 매일 자정(Asia/Seoul) 이력 초기화 스케줄러 |
| AI 뉴스 (`/news`) | LIVE | RSS 다중언론사(한국경제/SBS/동아일보/매일경제), Gemini 매일 07시 브리핑 |
| 지도 API (`/map`) | LIVE | 맛집(카카오)+부동산(국토부 실거래가), 탭 통합, localStorage로 뷰·검색결과 세션 유지 |
| 웹 게임 (`/game`) | LIVE(오목·숫자야구) | 오목: AI 대국(난이도/색상/제한시간), 순수 알고리즘(미니맥스), 외부 API 없음 |
| 장기 | **작업 중** | 규칙엔진(`JanggiGame`/`JanggiRules`) 백엔드까지 커밋됨. AI서비스·컨트롤러·프론트엔드 미착수 |
| 체스 | 미착수 | |
| 가격비교, 주식AI분석 | 미착수 | 아이디어 단계 |

## 지금 당장 이어서 할 일 (장기)

백엔드 파일 3개 커밋 완료:
- `src/main/java/com/jihun/portfolio/game/janggi/JanggiGame.java` — 대국 상태(보드 9x10, 메모리 보관)
- `src/main/java/com/jihun/portfolio/game/janggi/JanggiRules.java` — 기물 이동/장군/합법수 판정

**아직 안 만든 것**:
1. `JanggiAiService.java` — 오목의 `OmokAiService.java`를 참고해서 같은 패턴(난이도별 휴리스틱+미니맥스, 외부 API 없음)으로 구현
2. `JanggiService.java` — 오목의 `OmokService.java` 패턴(ConcurrentHashMap 인메모리 대국, `create`/`move`/`timeout`)
3. `JanggiController.java` — 오목의 `OmokController.java` 패턴. 단, move는 `{fromX,fromY,toX,toY}` 4개 좌표 필요(오목은 좌표 1개였음)
4. `game.html`에 장기 탭 추가 — 9x10 보드 UI, 기물 클릭 → 이동 가능 칸 하이라이트 → 클릭 이동 (오목처럼 클릭 한 번이 아니라 "말 선택 → 목적지 선택" 2단계 흐름 필요)
5. 기물 표시는 원형 말 안에 한글 한 글자(궁/사/상/마/차/포/졸·병) + 진영별 색상으로 표현 권장

**알아둘 것 — 간소화한 규칙**: 차·포·병(졸)의 궁성 대각선 특수 이동은 생략함(궁·사만 궁성 대각선 지원). 사용자에게 이미 고지했고 동의받음.

## 최근에 겪은 실수/함정 (반복하지 말 것)

- 부동산 실거래가 API 구주소 `openapi.molit.go.kr` → 신주소 `apis.data.go.kr`로 이전됨(https 필수)
- 네이버 지오코딩(`naveropenapi.apigw.ntruss.com`)은 정식 도로명주소 파싱용이라 "구 동 아파트명" 같은 자연어 검색에 빈 결과 반환 → **카카오 키워드검색으로 교체함**(`KakaoGeocodeService`). 부동산 좌표 지오코딩은 카카오를 쓴다.
- 카카오 로컬 API의 `total_count`는 페이지네이션에 못 씀(최대 4만+ 나옴). 반드시 `pageable_count`(최대 45페이지) 사용.
- 카카오 로컬 API로 현위치 검색 시 `x,y,radius,sort=distance` 파라미터를 반드시 붙여야 실제 반경 검색이 됨(안 붙이면 그냥 전국 키워드 검색).
- 외부 API를 부를 때는 반드시 연결/응답 타임아웃을 명시할 것(`SimpleClientHttpRequestFactory`) — 안 그러면 프론트가 무한 로딩됨.
- GitHub `push_files`/`create_or_update_file` 도구가 가끔 "타임아웃" 에러를 반환하지만 실제로는 커밋에 성공하는 경우가 있음 — 에러 나면 재시도하기 전에 `get_file_contents`로 실제 반영 여부 먼저 확인할 것.

## 홈페이지(`index.html`) 문구 원칙 — 중요

포트폴리오 사이트라 **개인적 서사·의견·감정 표현을 배제**하고, 실제 적용된 기술 위주로 간결·담백하게 작성할 것. ("직접 배포하고 계속 쌓아가는 공간", "~를 중심으로 장르를 가리지 않고" 같은 문구는 사용자가 명시적으로 싫어함.)

## 환경변수 (운영 서버 `conf/app.conf`, 템플릿은 `scripts/conf/app.conf`)

`SPRING_PROFILES_ACTIVE`, `DB_HOST/NAME/USER/PASSWORD`, `GEMINI_API_KEY`, `NAVER_MAP_CLIENT_ID`, `NAVER_MAP_CLIENT_SECRET`, `KAKAO_API_KEY`, `REALESTATE_API_KEY`

## 코딩 컨벤션

- 새 기능은 `com.jihun.portfolio.<기능>` 패키지 + `/api/<기능>/*` REST API + 정적 페이지 1장(또는 기존 통합 페이지에 탭 추가) 방식으로 확장
- 프론트엔드 공통 스타일은 `/css/base.css`, 공통 네비게이션은 `/js/nav.js` (새 메뉴는 `NAV_ITEMS` 배열에 한 줄 추가)
- 다크 네이비(#0B101B) + 앰버(#F2A93B) 액센트 컬러 테마 유지
- 여러 게임을 만들 계획이므로 게임별 인메모리 대국 상태 + 공통 `GameScore` 랭킹 테이블 패턴 유지
