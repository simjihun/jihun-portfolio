# CLAUDE.md — hunit-portfolio 프로젝트 핸드오프 문서

Claude Code(또는 다른 Claude 세션)가 이 레포를 처음 열었을 때 빠르게 맥락을 잡기 위한 문서입니다.

## 개발 환경

- **IDE**: Eclipse IDE 2025-03 (4.35.0) 사용. Eclipse 관련 안내(임포트·병합 방법 등)를 할 때는 이 버전 기준으로 답한다.

## 사이트 개요

- **사이트**: https://hunit.kr
- **스택**: Java 21, Spring Boot 3.5, JPA, MySQL(RDS)/H2(로컬), Vanilla JS + Chart.js
- **인프라**: AWS EC2 + nginx + Let's Encrypt + GitHub Actions CI/CD
- **배포**: `main` 브랜치 push → 자동 빌드/배포
- **로컬 실행**: `mvn spring-boot:run` (포트 8081, H2)

## 콘텐츠 작성 원칙 (2026-08-12, 확정 — 필독)

특정 회사·직무를 겨냥한 어필용 문구를 걷어내고, 담백한 기술 문서로 전환한다. **README.md, CLAUDE.md 자체,
hunit.kr 사이트(메인화면·기능 페이지·게임 설명) 전부**에 동일하게 적용하고, 앞으로 추가되는 모든 기능도 처음부터
이 기준으로 작성한다. 단, **비공개 회원 영역(`/login` `/signup` `/mypage` `/admin`)은 README.md에 절대 언급하지
않는다** — 공개 포트폴리오로 내보이는 대상이 아니라 개인·지인 전용이라, 존재 자체를 공개 문서에 노출하지 않는다.

- **주관적 의견·감상·어필 문구 금지**: "정교한", "매끄러운", "강력한", "직접 만들고 쌓아가는 공간" 같은
  형용사·서사 문구를 쓰지 않는다. 사실만 나열한다.
- **특정 기업/직무를 겨냥한 강조 금지**: 이력서 문맥에 맞춰 특정 기술을 과장해서 부각하지 않는다. 실제로
  적용된 기술만, 적용된 만큼만 적는다.
- **알고리즘·로직·클래스 구조 위주**: 무엇을 썼는지 나열하는 대신 어떻게 동작하는지(알고리즘, 자료구조,
  클래스/패키지 구조, 데이터 흐름)를 적는다.
- **간결함**: 긴 문장형 설명 대신 짧은 불릿. 한 불릿에 한 사실만 담는다.
- **사이트에 노출되는 문구에는 대화·개발 과정의 사견을 옮기지 않는다(2026-08-18, 확정)**: 사용자와 Claude가
  나눈 대화, 코드 주석, 커밋 메시지에 있는 설명(왜 이렇게 만들었는지, 무엇을 의도적으로 생략했는지, "~라서
  일부러 이렇게 했다" 같은 서술)은 사이트 화면(배너·안내문·버튼 설명·툴팁 등)에 그대로 옮기지 않는다.
  사이트에는 **적용된 기술이나 알고리즘을 어떻게 구현했는지**만 간단명료하게 적는다. 코드 주석·커밋
  메시지·이 문서(CLAUDE.md)에는 그런 설명을 자유롭게 남겨도 되지만, 화면에 실제로 렌더링되는 텍스트는
  항상 이 기준으로 다시 점검한다. (사례: `server-control.html`의 초기 안내 배너에 "실제 서버를 SSH로
  제어하지 않는 시뮬레이션입니다", "10년 전 흔했던 스타일로 일부러 코딩했습니다" 같은 문구가 들어갔던 것을
  발견 후 담백한 기술 설명으로 교체함)
- **게임 설명(웹게임/보드게임/카드게임 공통)**: 조작법·승리조건·핵심 규칙을 짧은 불릿으로만 작성한다.
  긴 문장을 이어 붙이지 않는다. 사용자에게 굳이 필요 없는 내부 구현(난이도 편향 로직 등)은 계속 노출하지
  않되, 플레이어가 알아도 되는 규칙(완성 조건, 이동 규칙 등)은 불릿으로 짧게 적는다.
- **메인화면(index.html) 기능 섹션**: `.skills` 칩(적용 기술) + `<ul>` 불릿(알고리즘/동작 방식, 3~4개,
  각 한 줄) 조합만 사용한다. 링크·CTA 문구도 군더더기 없이.
- **README.md**: 프로젝트 한 줄 소개 + 기술 스택 목록, 기능별로 "적용 기술 → 핵심 로직" 짧은 불릿. 자기소개·
  성장 서사·이력 강조 문구는 넣지 않는다. 클래스/패키지 구조, API 엔드포인트처럼 검증 가능한 사실 위주로 쓴다.

## 모바일 최적화 원칙 (2026-08-14, 확정)

새 기능을 개발할 때는 처음부터 모바일 화면에서 한눈에 보기 좋도록 설계한다. 데스크톱 레이아웃을 먼저
만들고 나중에 모바일용으로 줄이는 순서가 아니라, 좁은 화면(360~400px) 기준으로 무엇이 필수 정보인지
먼저 고르고 배치한다.

- **CSS Grid/Flex 오버플로 함정**: grid/flex 자식 요소는 기본값이 `min-width:auto`라, 숫자나 긴 텍스트가
  든 셀은 부모 폭보다 넓게 밀려날 수 있다. `base.css`가 `html`/`body`에 `overflow-x:hidden`을 걸어둬서,
  이렇게 삐져나간 내용은 스크롤도 없이 그냥 잘려서 안 보이게 된다 — "표/차트가 화면 밖으로 나가 있다"는
  증상의 대표 원인. 표를 grid/flex로 흉내 낸 컴포넌트(호가창 등)를 만들 때는 셀 요소에 `min-width:0`을
  명시하고, 필요하면 `overflow:hidden;text-overflow:ellipsis`도 같이 준다.
- **진짜 `<table>` 요소**는 `.table-scroll`(`overflow-x:auto`)로 감싸 가로 스크롤을 확보하되, `base.css`의
  전역 `table{min-width:560px}`가 모바일 폭보다 넓어서 스와이프 없이는 안 보이는 문제가 있었다 — 덜
  중요한 컬럼은 `@media (max-width:600px)`에서 `display:none`으로 숨기고 나머지만 스와이프 없이 한
  화면에 들어오게 하는 편이 스와이프를 유도하는 것보다 낫다.
- **로딩 상태는 눈에 띄게 표시한다**: 회색 텍스트만으로는 로딩 중인지 알아채기 어렵다는 피드백이 있었다
  — 회전 스피너(`.loading-row`+`.spinner`, `stock.html` 참고)처럼 움직임이 있는 표시를 기본으로 쓴다.
- 배포 전 실제 모바일 폭(360~400px)에서 스크린샷으로 확인하는 것을 기본 점검 항목으로 삼는다.

## Gemini API 호출 정책 (2026-08-14, 확정)

무료 티어 일일 한도가 제한적이라, Gemini를 쓰는 모든 기능은 아래 정책을 따른다.

- **방문자 요청 시점에 직접 호출하지 않는다**: 방문자가 몇 명이든 호출량이 늘어나지 않도록, 생성은
  스케줄러가 정해진 시각에만 수행하고 DB에 저장해둔다. 방문자 요청은 저장된 값을 읽기만 한다. (예외:
  종목별 AI 요약처럼 "버튼을 눌렀을 때만" 만드는 온디맨드 기능은 이 정책 대상이 아니지만, 대신 결과를
  길게(예: 12시간) 캐시해 같은 대상 재요청 시 재호출하지 않는다.)
- **실패 시 재시도는 짧고 제한적으로**: 실패하면 몇 초 후 딱 1회만 재시도하고, 그래도 실패하면 포기하고
  기존 DB 데이터(이전 값)를 그대로 유지한다. 무제한 재시도는 한도를 더 빨리 소진시킬 뿐이다.
- **모델 호출은 공용 `GeminiClient`(`com.jihun.portfolio.common`)를 통해서만 한다**: 직접
  `RestTemplate`/`HttpClient`로 Gemini API를 부르는 코드를 새로 만들지 않는다. `GeminiClient`는 후보
  모델 목록을 순서대로 시도하고, 모델이 없어졌거나(404) 일시 과부하(5xx)면 다음 후보로 자동으로 넘어간다
  — 예전엔 뉴스 브리핑이 모델을 하나만 하드코딩해서 그 모델이 없어지자(404) 전부 실패했고, 주식 브리핑은
  폴백 목록은 있었지만 503에서는 다음 모델로 안 넘어가고 포기해버리는 버그가 각각 있었다.
- **DB에 아직 데이터가 없을 때(배포 직후 등)는 재시작 시 1회 즉시 채워둔다**: 다음 스케줄 시각까지
  기다리게 하지 않는다. `@PostConstruct` + 별도 데몬 스레드로 구현(뉴스/주식 브리핑 서비스 참고) — 데몬
  스레드라 앱 기동 자체는 지연시키지 않는다.
- **Gemini 응답을 저장하는 DB 컬럼은 `@Lob`만 믿지 말고 `columnDefinition = "LONGTEXT"`를 명시한다**:
  `@Lob`만으로는 MySQL에서 충분히 큰 컬럼이 안 만들어져 "Data too long" 오류로 저장이 실패한 사례가
  있었다(`StockAiBriefing`).

## 메뉴/메인화면 순서 규칙

메뉴·메인화면 기능 섹션 순서 고정: **AI 뉴스 → AI 주식 → 지도 API → MMS → 서버제어 → 웹 게임(웹게임/보드게임/카드게임 통합)**.
새 기능 배포 시 `index.html` 기능 섹션과 `nav.js`의 `NAV_ITEMS`에 이 순서로 동시 반영한다.
**단, 비공개 회원 영역은 nav.js·index.html 어디에도 등록하지 않는다** — 직접 URL로만 접근 가능해야 한다.

## URL 구조

- `/news`, `/stock`, `/map`(`/map/food`, `/map/estate`), `/mms`, `/server-control`
- `/game`(웹게임: 숫자야구·발리볼), `/game/board`(보드게임: 오목·장기), `/game/card`(카드게임: 프리셀·클론다이크·스파이더)
- 상단 "웹 게임" 메뉴는 드롭다운으로 웹게임/보드게임/카드게임 3갈래를 노출(`nav.js`)
- `/login`, `/signup`, `/mypage`(로그인 후 기본 랜딩 = 대시보드), `/mypage/settings`(비밀번호 변경 등 계정 설정),
  `/admin`(관리자 전용 기능 허브) — 비공개 회원 영역(공개 nav 미노출). `/admin` 하위 기능은
  `/admin/<기능영문>[/<세부기능영문>]` 패턴으로 계속 확장한다(예: `/admin/members`).

## 기능별 현황

| 기능 | 경로 | 상태 | 비고 |
|---|---|---|---|
| AI 뉴스 | `/news` | LIVE | 다중언론사 RSS, Gemini 매일 07시 브리핑(+배포 직후 DB 비어있으면 즉시 1회), 문자 2-그램 유사도 기반 근사중복 제거, 카테고리별 최소 1건+이미지 |
| AI 주식 | `/stock` | LIVE | 토스증권 Open API+업비트+Gemini. 상세는 아래 참고 |
| 지도 API | `/map` | LIVE | 맛집(카카오)+부동산(국토부). 상세는 아래 참고 |
| MMS | `/mms` | LIVE | 워커 5개, 다중발송, 자정 초기화 |
| 서버제어 | `/server-control` | LIVE | MMS 발송 인프라 5대 서버 상태 조회·제어, 리소스 폴링 차트, SQL 콘솔, INSERT/UPDATE 연습 테이블. 상세는 아래 참고 |
| 웹게임 | `/game` | LIVE(숫자야구·발리볼) | 발리볼: 물리 전부 클라이언트(Canvas+rAF), 서버는 결과만 저장. 랭킹 상시 노출 |
| 보드게임 | `/game/board` | LIVE(오목·장기) | 장기: 알파베타 완전탐색 AI. 오목: 난이도/착수수/클리어시간 랭킹 |
| 카드게임 | `/game/card` | LIVE(프리셀·클론다이크·스파이더) | 카드 로직 전부 프론트엔드, 카드 이미지는 자체 저장한 SVG 라이브러리(cardmeister.js). 상세는 아래 참고 |
| 비공개 회원 영역 | `/login` `/mypage` `/admin` | LIVE | Spring Security 세션 인증, 관리자 승인제, 로그인 24시간 유지, 로그인 시 대시보드(AWS/DB 정보) 랜딩. 상세는 아래 참고. **공개 문서(README 등)에 노출 금지** |
| 체스, 가격비교 | - | 미착수 | |

## AI 주식 메모 (`/stock`)

- `com.jihun.portfolio.stock`: `TossApiClient`(OAuth+전역 스로틀+429재시도), `StockDashboardService`(TTL캐시), `StockController`, `StockAiBriefing`/`StockAiBriefingRepository`(브리핑 스냅샷 DB)
- 스키마 함정: RankingItem엔 종목명/시총 없음(종목마스터 조인), changeRate는 소수비율(×100 필요), exchange-rate는 baseCurrency/quoteCurrency 필수+등락률 자체계산
- 캘린더는 토스 API 자체가 전일/당일/익일 3영업일만 반환한다(더 긴 범위 불가 — API 제약). 프론트는 개장일 포함 그대로 표시, 데이터가 없으면 "주요일정이 없습니다" 안내만 표시
- **AI 브리핑(시황 요약·주목종목·체크포인트)은 방문자 요청 시점에 Gemini를 부르지 않는다** — 하루 3회(09/15/21시 KST) 스케줄러가 미리 생성해 DB(`StockAiBriefing`, 단일 row)에 저장해두고 `getAiBriefing()`은 그 값만 읽는다. 실패 시 5초 후 1회만 재시도, 그래도 실패하면 DB를 건드리지 않아 자동으로 이전 데이터가 유지됨. 배포 직후 DB가 비어있으면 앱 시작 직후 1회만 별도로 채워둠(`@PostConstruct`). Gemini 호출 자체는 공용 `GeminiClient`가 담당 — 상세는 위 'Gemini API 호출 정책' 참고
- 검색 성능: 종목별 일봉 조회(`fetchDailyStats`)를 전용 스레드풀(`searchIoExecutor`)로 병렬 디스패치 — `TossApiClient` 전역 스로틀(300ms)은 유지하되, 순차 호출 시 응답 대기 시간이 계속 누적되던 문제를 해결(검색 결과 최대 10종목 기준 체감 대기시간 크게 감소)
- 프론트: 랭킹 테이블은 폴링마다 지우지 않고 심볼키로 재사용+FLIP 재정렬+변경셀 플래시. 국기는 flagcdn.com 이미지(이모지는 Windows에서 깨짐)
- 서버설정 필요: `TOSS_CLIENT_ID/SECRET`, 토스 허용IP 등록

## 지도 API 메모 (`/map`)

- `com.jihun.portfolio.map`: `MapController`(맛집 검색, 카카오 Local API 프록시), `RealEstateController`(국토부
  실거래가), `KakaoGeocodeService`(좌표 지오코딩), `RealEstateRegions`
- 맛집 지도와 부동산 시세는 완전히 독립된 네이버 지도 인스턴스·마커 배열·리스트를 유지한다 — 탭을 옮겨도
  서로의 검색 결과가 사라지지 않으며, 각자 마지막 뷰포트·검색 상태를 localStorage에 저장해 새로고침해도 복원된다
- **카테고리 필터**: 카카오 공식 `category_group_code`(FD6=음식점, CE7=카페, CS2=편의점, MT1=대형마트)로
  서버단 필터링. **검색어가 비어있고 카테고리만 선택된 경우 카카오 키워드검색(`keyword.json`) 대신 카테고리
  검색(`category.json`)으로 자동 분기한다** — 편의점·마트처럼 이름에 검색어가 잘 안 붙는 카테고리를 키워드
  검색과 결합하면(예: 검색창에 남아있던 "맛집" + 카테고리 CS2) 결과가 거의 안 나오는 문제가 있어서다.
  카테고리 검색은 위치(x,y)가 필수라, 검색/현위치 버튼을 직접 눌렀을 때는 검색어·카테고리가 둘 다 비어있어도
  현재 지도 중심 좌표를 붙여 항상 실행된다(`performFoodSearch`의 `forceSearch` 파라미터)
- **검색어를 임의로 채우는 로직은 어디에도 두지 않는다(확정)**: 과거 현위치/재검색 버튼 클릭 시 빈 검색창에
  "맛집"을 자동으로 채워 넣었다가, 카테고리가 편의점·마트로 확장되면서 그 기본값과 결합해 검색이 실패하는
  문제가 반복적으로 발생해 전부 제거했다. 새 진입점을 추가할 때도 이 원칙을 유지할 것
- **거리순 정렬**: 카카오 응답의 `distance`(m) 필드를 목록에 "540m"/"1.2km"로 표시하고, 필터 영역에
  "현위치 기준"/"이 지역 기준"/"지도 중심 기준" 참조 라벨을 함께 노출
- **마커 클러스터링**: 네이버 지도 공식 유틸(`navermaps/marker-tools.js` 저장소의
  `marker-clustering/src/MarkerClustering.js`, jsDelivr GH 프록시로 로드) 사용 — 실제 렌더링 지도가 카카오맵이
  아니라 네이버 지도이므로 카카오 클러스터러가 아닌 이 라이브러리를 쓴다. 로딩 실패에 대비해 try/catch로
  감싸고, 실패 시(`MarkerClustering` 전역 없음) 클러스터링 없이 마커를 지도에 직접 붙이는 폴백 경로를 둠
- **마커 디자인**: 네이버 지도 방식을 따라 평소엔 12px 빨간 점만 표시, 마우스오버/클릭 시에만 카테고리
  이모지(🍴☕🏪🛒) 원형 아이콘 + 상호명 라벨로 커진다. 클릭한 마커는 선택 상태로 고정되고 다른 마커를
  클릭하면 원래대로 돌아감(`setActiveFoodMarker`) — 마커가 많이 뭉쳤을 때 숫자 배지보다 훨씬 덜 어수선함
- 부동산 마커는 별도 스타일(파란 가격 라벨, `#5AA2FF`) 유지, 아파트명+동 단위로 마커를 묶어 같은 단지는
  마커 하나만 생성
- `localStorage` 검색 상태 캐시 키에는 버전을 붙인다(`searchState:food:v2`) — 캐시 스키마나 기본 동작이
  바뀌면 버전을 올려 예전 캐시를 자동으로 무시하게 할 것(사용자가 직접 캐시를 지울 필요 없게)
- 페이지 로드 직후 localStorage 세션 복원 시, 지도 컨테이너 레이아웃이 아직 안정되기 전에 마커가 추가되어
  핀이 화면에 안 뜨는 문제가 있었음 — 복원 직후 200ms 뒤 `resize` 이벤트를 강제로 한 번 더 발생시켜 재보정

## 서버제어 메모 (`/server-control`)

- `com.jihun.portfolio.servercontrol`: 계층형 Controller→Service→DAO 구조, JPA 대신 `JdbcTemplate`으로 SQL을 직접 작성
- `ServerVo`/`ServerMetricVo`/`ServerControlLogVo`: 서버 마스터, 리소스 지표 이력, 제어 이력
- `PracticeTemplateVo`/`PracticeScheduleVo`: INSERT/UPDATE 연습 전용 테이블(`mms_practice_template`,
  `mms_practice_schedule`)의 VO — 아래 참고
- `ServerMetricScheduler`가 10초마다 가동중인 서버마다 CPU/MEM/DISK를 직전값 기반 랜덤워크로 갱신해 `admin_server_metric`에 적재 — 프론트는 5초 주기로 폴링해 Chart.js에 반영
- 서버 제어(시작/중지/재시작)는 `admin_servers.status` 값을 갱신하고 `admin_server_control_log`에 이력을 남기는 방식으로 동작
- **SQL 콘솔은 화이트리스트 기반으로 제한된 범위에서만 실행된다**: 사용자가 입력한 쿼리는 `SELECT`로
  시작하는지, 금지 키워드(INSERT/UPDATE/DELETE/DROP/ALTER/TRUNCATE/GRANT 등)를 포함하지 않는지, FROM/JOIN
  대상 테이블이 허용 목록(`mms_practice_message_log`, `mms_practice_server_config`, `mms_practice_template`,
  `mms_practice_schedule`)에만 해당하는지를 `ServerControlServiceImpl.executeQuery()`에서 검사한 뒤
  `JdbcTemplate.queryForList()`로 실행한다. 인증 없는 공개 페이지이므로 이 검증을 절대 느슨하게 하지 않는다
  (운영 테이블 접근·쓰기 계열 SQL 전부 차단). 콘솔 상단의 테이블 선택 드롭다운 + "전체 조회"/"선택 서버만
  조회" 버튼으로 기본 SELECT 문을 직접 타이핑하지 않고도 만들어 실행할 수 있다
- **INSERT/UPDATE는 SQL 콘솔이 아니라 전용 모달 폼으로만 한다**: `mms_practice_template`(MMS 템플릿)와
  `mms_practice_schedule`(발송 스케줄) 두 테이블은 SQL 콘솔에서 조회는 되지만, 쓰기(INSERT/UPDATE)는 원본
  SQL 문자열을 실행하지 않고 파라미터 바인딩된 `JdbcTemplate.update()`(`/api/servercontrol/template/*`,
  `/api/servercontrol/schedule/*` 엔드포인트 + 모달 폼)로만 이루어진다. 기존 message_log/server_config
  테이블은 계속 조회 전용으로 보호된다
- 프론트는 Bootstrap 3 + AdminLTE 2 CDN 기반 레이아웃(사이드바 서버 목록 + 상단 요약 박스 + box 패널), 다크 톤으로 커스텀 오버라이드

## 웹게임 · 발리볼 메모 (`/game`, volleyball 탭)

- 물리(중력·충돌·반사·캐릭터 이동)는 전부 프론트엔드(Canvas+requestAnimationFrame)에서 계산, 서버(`VolleyballController`)는 대결 종료 후 최종 스코어만 `GameScore`에 저장
- 캐릭터는 몸통+머리를 하나의 계란형으로 붙인 단순한 병아리 실루엣(별도 부위 이어붙이기보다 단순한 형태가 렌더링이 안정적임)
- 타격 시 캐릭터의 그 순간 상하 속도(점프 상승 중=토스, 하강 중=스매시)를 반영해 물리적 느낌 부여
- 난이도 선택 없이 AI는 공 궤적을 프레임 단위로 시뮬레이션해 낙하지점을 예측
- 설정 화면부터 랭킹보드를 항상 표시(게임 시작 전에도 `/game/volleyball` 랭킹 조회)

## 보드게임 메모 (`/game/board`)

- 오목·장기 모두 대국 상태는 서버 메모리에 보관(`OmokService`/`JanggiService`, `ConcurrentHashMap`), 재접속 세션 유지 불필요
- 오목: `OmokController`가 승리한 대국만 `/score`로 기록(서버가 `getStatus()`+`getHumanColor()`로 승자 검증). 착수 수(`moveCount`)·난이도는 서버 값을 쓰고, 소요시간만 프론트엔드가 측정해 전달. gameType `OMOK_EASY/MEDIUM/HARD`, 랭킹은 3난이도를 합쳐 착수 수 오름차순 상위 10개
- 장기: 알파베타 완전탐색 AI(EASY/MEDIUM 2플라이, HARD 4플라이), 마·상 포진 4종, `move()`는 사람 수만 반영하고 `aiMove()`를 별도 엔드포인트로 분리해 장군 표시를 확보. 궁·사·차·포는 궁성 대각선 지원(포는 중앙 기물을 넘어야 함), 병의 궁성 대각선 이동은 간소화로 생략
- 두 게임 모두 설정 화면부터 랭킹보드를 항상 표시

## 카드게임 메모 (`/game/card`)

카드 로직(이동 유효성, 승리·막힘 판정)은 전부 프론트엔드에서 계산하고, 서버는 결과만 `GameScore`에 저장하는 구조를
프리셀에서 정립해 클론다이크·스파이더도 그대로 따른다. 새 카드게임 추가 시 이 구조와 아래 카드 이미지 방식을 재사용한다.
세 게임 모두 설정 화면부터 랭킹보드를 항상 표시.

**카드 이미지 (모든 카드게임 공통)**: 자유 라이선스(Unlicense) SVG 카드 라이브러리 `cardmeister`
(https://github.com/cardmeister/cardmeister.github.io)의 `elements.cardmeister.min.js`를 `/js/cardmeister.js`로
저장해 `<playing-card cid="AS">` 커스텀 엘리먼트로 렌더링한다. `cid`는 랭크문자(A,2..10→T,J,Q,K)+무늬문자(S/H/D/C).
카드 뒷면은 라이브러리의 `cid="F0"` 대신 `/images/cards/back.svg`(고정 이미지 파일)를 `<img>`로 재사용한다 —
카드 여러 장을 겹쳐 쌓을 때 경계 구분이 더 뚜렷하다.

### 프리셀 (`com.jihun.portfolio.game.freecell.FreecellController`)
- 뭉치 이동 유효성, 서플무브 용량, 안전 자동정리, 승리·막힘 판정 전부 프론트엔드. gameType `FREECELL_EASY/MEDIUM/HARD`, metric=이동횟수 오름차순
- 뭉치 이동 가능 장수 = (빈 오픈칸 수+1) × 2^(빈 컬럼 수) — 목적지 자신이 빈 컬럼이면 그 칸은 배수 계산에서 제외
- 난이도는 초기 배치로만 구분: 완전 무작위 셔플 후 각 컬럼 안에서 A·2의 위치만 재배치(초급=꺼내기 쉬운 위치, 고급=깊숙이, 중급=무편향). 이 구현 디테일은 사용자에게 보이는 규칙 문구에는 넣지 않는다
- 완전한 솔버가 아니라 휴리스틱 기반 난이도

### 클론다이크 (`com.jihun.portfolio.game.klondike.KlondikeController`)
- 오픈칸이 없어 뭉치 이동에 용량 제한이 없음(노출된 유효한 연속열이면 통째 이동). 스톡→웨이스트로 카드를 넘기는 방식이 핵심
- 카드를 옮겨 컬럼의 새 맨 위 카드가 뒷면이면 자동으로 뒤집음(`kdFlipExposedTop`)
- 다시 섞기는 난이도 무관 항상 무제한. 초급=1장씩 넘기기+완성 칸 자동정리(`kdAutoSafe`, `isSafeForFoundation` 공용 함수 사용)+힌트 3회, 중급=1장씩+힌트 없음, 고급=3장씩+힌트 없음. gameType `KLONDIKE_EASY/MEDIUM/HARD`
- 힌트(`kdFindHint`/`kdUseHint`): 가능한 수를 전부 모아 점수를 매겨(완성 칸>뒷면 노출>빈 컬럼 확보>웨이스트 이동>그 외) 최고점 수를 하이라이트. 난이도별 `hints` 횟수만큼만 사용
- 빈 컬럼에는 K만 놓을 수 있음(프리셀은 아무 카드나 가능 — 게임마다 규칙이 다름)
- 막힘 판정(`kdHasAnyMove`)은 스톡/웨이스트에 카드가 남아있으면 항상 수 있음으로 판정, 그 외엔 노출된 연속열의 모든 시작 카드로 이동 가능성 확인

### 스파이더 (프론트엔드 로직만, `com.jihun.portfolio.game.spider.SpiderController`는 랭킹 API만)
- 104장(2벌)을 10개 컬럼에 배치(4개 컬럼 6장·6개 컬럼 5장). 카드 로직은 클론다이크와 마찬가지로 100% 프론트엔드
- 이동 규칙: 무늬 무관 1씩 내려가는 카드 위에 배치 가능, 단 같은 무늬로 연결된 뭉치만 통째로 이동 가능
- 완성 조건: K→A 같은 무늬로 13장 연결 시 자동 제거(`spCheckCompletedRun`), 8세트 완성 시 승리
- 난이도 = 사용 무늬 수(1/2/4종, 실제 정식 스파이더 변형). suitCount에 따라 같은 무늬를 8/4/2회씩 반복해 104장을 채움
- 스톡 클릭 시 10컬럼에 한 장씩 배분, 빈 컬럼이 있으면 배분 불가(표준 규칙)

## 비공개 회원 영역 메모 (`/login`, `/signup`, `/mypage`, `/mypage/settings`, `/admin`)

패키지 `com.jihun.portfolio.auth`(인증), `com.jihun.portfolio.admin`(대시보드). 공개 포트폴리오와 별개로
관리자가 승인한 회원만 쓸 수 있는 개인·지인 전용 영역이다. **README.md 등 공개 문서에는 이 기능을 절대
언급하지 않는다.**

- **비밀번호**: BCrypt 해시로만 저장(복호화 불가). 로그인 시 입력값을 같은 방식으로 해시해 비교
- **전화번호·이메일**: `CryptoService`가 AES-256-GCM으로 암호화해 저장. 중복확인·조회(비밀번호 찾기 등)를 위해
  HMAC-SHA256 기반 "조회 전용 해시"(`emailLookupHash`/`phoneLookupHash`)를 별도 컬럼에 함께 저장 — 이 해시는
  같은 입력이면 항상 같은 값이 나오지만 원문으로 되돌릴 수는 없음
- **가입 → 승인 → 로그인**: 가입 직후 상태는 `PENDING`이라 로그인 불가. 관리자가 `/admin/members`에서
  승인(`APPROVED`)해야 로그인 가능. `MemberDetailsService`가 PENDING을 disabled, REJECTED를 accountLocked로
  매핑해 Spring Security가 자동으로 막는다
- **최초 관리자 계정은 코드에 고정값**: `MemberService`의 `BOOTSTRAP_ADMIN_USERNAME`/`BOOTSTRAP_ADMIN_PASSWORD`
  상수(현재 `simering`/`admin`)로, 서버 시작 시 관리자 계정이 하나도 없을 때만 1회 생성(status=APPROVED로 바로
  활성화). **최초 로그인 직후 반드시 계정 설정에서 비밀번호를 변경할 것** — 소스에 남는 값이라 깃 이력에서도
  계속 보임. 이후 추가 관리자는 DB에서 role을 직접 바꾸거나 별도 승격 기능을 만들어야 함(현재 승격 UI 없음)
- **비밀번호 찾기 3단계**(`PasswordResetService`): 인증번호 요청 → 확인(성공 시 1회용 `resetToken` 발급) →
  `resetToken`으로만 실제 비밀번호 변경. 인증번호 자체는 비밀번호 변경에 직접 쓰이지 않음(토큰으로 한 단계 분리)
- **이메일만 실동작**(`EmailService`, Gmail SMTP 등 `MAIL_USERNAME`/`MAIL_PASSWORD` 필요). **SMS는 UI·API 형태만
  존재하고 실제 발송 미연동** — 채널 선택 시 SMS를 고르면 "준비 중" 안내만 반환한다. 추후 알리고/NHN Cloud 등
  붙일 때는 `PasswordResetService.requestCode()`의 채널 분기만 확장하면 됨
- **로그인 유지 24시간**: `server.servlet.session.timeout: 24h` + Spring Security `rememberMe`(토큰 유효기간
  24시간, `alwaysRemember(true)`로 체크박스 없이 항상 발급, 서명 키는 `app.security.encryption-key` 재사용).
  브라우저를 껐다 켜도 24시간 안에는 재로그인 없이 유지된다
- **[알려진 단순화] CSRF 보호 비활성화**: 템플릿 엔진 없이 정적 HTML+fetch 구조라 토큰을 헤더에 실어 보내는
  작업을 생략했다. 결제·금전 처리가 없는 개인 연습용 비공개 영역이라 우선순위를 낮췄음 — 나중에 강화하려면
  `/api/csrf-token` 같은 엔드포인트로 토큰을 내려주고 프론트에서 헤더에 실어 보내는 방식으로 다시 켤 수 있음
- **로그아웃**은 `<form method="post" action="/logout">`로 처리(CSRF 비활성화라 별도 토큰 불필요). remember-me
  쿠키도 함께 삭제됨(`deleteCookies("JSESSIONID","remember-me")`)

### 공통 네비게이션 (`private-nav.js`)

- `/mypage`(대시보드), `/mypage/settings`(계정 설정), `/admin`, `/admin/*` 전부 `<div id="private-nav"></div>` +
  `<script src="/js/private-nav.js"></script>`만 넣으면 상단바가 자동으로 붙는다
- **왼쪽(priv-left)** = 브랜드 + "대시보드" 링크 + `PRIVATE_NAV_ITEMS`(실제 기능 메뉴, 관리자 전용이면
  `adminOnly: true`) — 새 기능을 상시 메뉴에 노출하려면 이 배열에 한 줄만 추가
- **오른쪽(priv-right)** = "{이름}님 ▾" 드롭다운(`.priv-dropdown`) 안에 계정 설정 / (관리자만) 관리자 페이지 /
  로그아웃. 마이페이지·관리자 페이지 링크는 왼쪽 메뉴가 아니라 이 드롭다운에 있음 — 왼쪽 자리는 실제 기능
  전용으로 비워둔다

### 대시보드 (`/mypage`, `com.jihun.portfolio.admin`)

로그인 후 기본 랜딩 페이지. 관리자로 로그인했을 때만 AWS/DB 정보 패널이 보인다(일반 승인 회원은 안 보임 —
`/api/admin/**`가 ROLE_ADMIN 전용이라 어차피 조회가 막혀서, 프론트에서 role 확인 후 아예 패널을 숨김).

- **AWS 인스턴스 정보**(`AwsInstanceInfoService`): EC2 인스턴스 메타데이터 서비스(IMDSv2, `169.254.169.254`)를
  직접 조회 — 인스턴스 ID·타입·리전·가용영역·IP·AMI ID. **새 AWS 액세스키가 전혀 필요 없다**(EC2 안에서만
  응답하는 로컬 전용 API). 로컬 개발 환경이나 EC2 밖에서는 800ms 타임아웃 후 "가져오지 못함"으로 표시됨
- **사용량·프리티어 잔여일수는 아직 미구현**: 이건 AWS Cost Explorer/Billing API가 있어야 하는데, 그건 진짜
  IAM 액세스키를 서버에 저장해야 해서 보안 영향 범위가 인스턴스 메타데이터와 다르다. 붙이려면 최소 권한
  (billing/ce:GetCostAndUsage 조회 전용) IAM 사용자를 새로 만들어 `conf/private.conf`에 추가해야 함 — 사용자
  승인 필요, 아직 진행 안 함
- **DB 정보**(`DatabaseInfoService`): 앱이 이미 쓰는 `DataSource`로 `information_schema.tables`를 조회 —
  호스트·포트·DB명과 테이블별 행 수(추정)·용량(MB). **새 DB 계정도 필요 없음**(운영 계정에 기본 포함된 조회
  권한만 사용). MySQL 기준 쿼리라 로컬 H2 환경에서는 실패 메시지만 반환

### 관리자 허브 구조 (`/admin`, `/admin/<기능>[/<세부기능>]`)

- `/admin`은 카드형 메뉴(`hub-grid`/`hub-card`, base.css)만 있는 랜딩 페이지. 실제 기능은 전부 하위 경로에 있다
  (예: 회원 관리 = `/admin/members`, `admin-members.html`)
- 새 관리자 전용 기능을 추가하는 절차:
  1. `WebConfig`에 `/admin/<기능>` (필요하면 `/admin/<기능>/<세부기능>`도) 라우팅 추가
  2. 정적 페이지에 `<div id="private-nav"></div>` + `<script src="/js/private-nav.js"></script>`만 넣기
  3. 완성 전이면 `/admin/admin.html`의 `hub-grid`에 `class="hub-card soon"`(href 없음)으로 먼저 등록해두고,
     완성되면 `class="hub-card"` + `href`로 바꿔 정식 노출
  4. 메뉴 링크에 상시 노출하고 싶으면 `private-nav.js`의 `PRIVATE_NAV_ITEMS` 배열에 한 줄 추가
- 인가는 이미 `SecurityConfig`의 `"/admin/**"` 매칭이 전부 커버하므로, 새 기능을 추가해도 보안 설정을 따로
  건드릴 필요가 없다

### 정책: 타인 플랫폼 콘텐츠/데이터 스크래핑 기능 금지

이 프로젝트에서는 비공개 영역이라도 다음 종류의 기능을 만들지 않기로 확정했다(요청 이력 있음, 매번 같은
이유로 거절):
- 인스타그램 등에서 아이디를 입력하면 그 계정의 사진·릴스·스토리를 가져와 보여주거나 다운로드하는 기능
- 잡플래닛·블라인드처럼 그 서비스의 핵심 유료 데이터(기업 리뷰 등)를 스크래핑해 검색·열람하게 하는 기능

이유: 대상 플랫폼의 이용약관이 스크래핑을 명시적으로 금지하고 있고(개인적 비영리 사용 목적이어도 마찬가지),
기술적으로는 그 플랫폼의 봇 탐지·로그인 장벽·내부 API 난독화를 우회해야만 동작한다는 공통점이 있다 —
CAPTCHA 우회나 DRM 크랙 도구를 만들지 않는 것과 같은 이유로, 결과물의 용도가 개인적·비상업적이어도
"플랫폼이 막아둔 자동 접근을 뚫는 기능" 자체는 만들지 않는다. 비슷한 다운로드 사이트·npm 패키지가 이미
많이 존재한다는 사실도 이 판단을 바꾸지 않는다(YouTube 스트림리퍼도 마찬가지 위치에 있고, RIAA와의
소송에서 미국 법원이 "다운로드 방지 기술 우회"로 보고 있다).
인스타그램 관련 기능이 필요하면 ① 본인 계정을 비즈니스/크리에이터로 전환해 공식 Graph API로 본인 데이터만
다루거나, ② 본인이 직접 업로드한 사진으로 인스타그램 스타일 UI만 재현하는 방향으로만 진행한다.

## 프론트 프레임워크 원칙

신규 기능은 순수 JS보다 Vue.js/React(CDN, 빌드 없이) 우선 검토. 기존 페이지 통째 마이그레이션은 불필요, 신규 단위로 점진 도입.
카드게임은 예외적으로 Vanilla JS + Web Components(cardmeister의 커스텀 엘리먼트) 패턴을 유지한다.
서버제어(`/server-control`)는 예외적으로 Bootstrap 3 + AdminLTE 2(CDN) + jQuery 조합을 유지한다.

## 최근 실수/함정

- MOLIT API 신주소 `apis.data.go.kr`, 네이버 지오코딩 대신 카카오 키워드검색, 카카오 `pageable_count` 사용, 현위치검색엔 `x,y,radius,sort=distance` 필수
- 지도 검색어가 비어있을 때의 기본값을 하드코딩(예: 항상 "맛집")하면, 카테고리가 늘어날수록(편의점·마트 등)
  그 기본 키워드와 무관한 카테고리 조합에서 검색 결과가 급감하거나 0건이 되는 문제가 생긴다 — 키워드가
  없을 때는 카카오 카테고리 전용 검색 API(`category.json`)로 분기하거나, 최소한 사용자 입력을 임의로
  채우지 말고 명시적으로 위치 좌표만 붙여서 보낼 것(지도 API 메모의 "검색어를 임의로 채우는 로직" 참고)
- 네이버 지도 마커 클러스터링 공식 유틸은 `navermaps/marker-clustering`이 아니라 `navermaps/marker-tools.js`
  저장소의 `marker-clustering/src/MarkerClustering.js` 경로다 — CDN 경로 오타로 스크립트 로딩이 404 나면
  그 뒤 `await` 체인이 전부 중단되어 지도 자체가 안 뜨는 심각한 장애로 이어질 수 있으니, 외부 스크립트
  로딩은 항상 try/catch로 감싸 실패해도 핵심 기능(지도 표시)은 유지되게 할 것
- localStorage에 저장하는 캐시 스키마나 기본 동작을 바꿀 때는 키 이름에 버전을 붙여라(`searchState:food`
  → `searchState:food:v2`) — 그러지 않으면 예전 버그 시절 저장된 값이 사용자 브라우저에 계속 남아, 서버
  코드는 고쳤는데도 캐시가 복원되면서 예전 증상이 재현된 것처럼 보인다
- 페이지 로드 직후 localStorage에서 세션을 복원해 지도 마커를 그리는 경우, 웹폰트 로딩 등으로 레이아웃이
  아직 자리잡기 전이라 지도 컨테이너 크기가 실제보다 작게 잡혀 마커 위치 계산이 어긋날 수 있다 — 복원
  직후 곧바로 렌더링하는 것 외에, 약간의 지연(200ms 등) 후 한 번 더 리사이즈 이벤트를 강제로 쏴서 재보정할 것
- 사이트 공통 상단 메뉴(`.topnav`)는 base.css에서 `position:fixed`다 — AdminLTE 같은 별도 UI 프레임워크를
  페이지에 얹을 때 z-index 문제를 고치겠다고 position을 relative로 바꾸면, body의 padding-top(고정 헤더용으로
  미리 비워둔 공간)이 그대로 빈 여백으로 남는다. fixed는 z-index 값과 무관하게 이미 별도 스태킹 컨텍스트를
  만드므로, z-index만 조정하고 position은 건드리지 말 것
- Bootstrap 3의 `.table > tbody > tr.active > td`는 `!important`로 배경색을 강제한다 — 커스텀 다크 테마에서
  행 선택 하이라이트 색을 바꾸려면 `tr.active`가 아니라 `tr.active > td`(같은 우선순위 + `!important`)를
  오버라이드해야 함
- `StringBuilder`로 문자열을 이어붙이다가 실수로 다른 이름의 `String` 변수에 `.append()`를 호출하는 오타는
  로컬에서 컴파일 확인 없이 커밋하면 CI에서만 발견된다(`cannot find symbol: method append`) — 여러 분기를
  가진 URL/쿼리 조립 코드를 짤 때는 커밋 전에 변수명이 실제로 일관되게 쓰였는지 diff를 한 번 더 확인할 것
- 외부 API는 반드시 타임아웃 명시
- SVG `vector-effect="non-scaling-stroke"`는 얇은 선을 사실상 안 보이게 만듦 — 빼야 함
- 이모지 국기는 Windows에서 깨짐 → flagcdn.com 이미지 사용
- 실시간 테이블은 innerHTML 통째로 갈아엎지 말고 키 기반 재사용+부분갱신
- GitHub 파일 편집 도구가 타임아웃 에러를 내도 실제로는 성공한 경우가 있음 — 재시도 전 `get_file_contents`로 확인, sha는 항상 직전에 새로 조회. 도구가 아예 응답 없이 멈추면(로컬 MCP 서버 다운) 사용자에게 Claude Desktop/MCP 서버 재시작을 요청한다. 재시작 후에는 실제로 커밋이 반영됐는지 `get_file_contents`로 먼저 확인하고 이어간다(반영 안 됐으면 재커밋)
- 대용량 파일(50KB 이상)을 통째로 커밋할 때 한글·이모지가 드물게 깨져서 올라간 사례가 있었음 — 커밋 직후 `get_file_contents`로 핵심 문자열(고유명사·이모지)이 정확한지 재확인
- 카드 문양이나 카드 뒷면을 유니코드·CSS·전용 cid로 매번 다시 그리는 방식은 렌더링이 불안정하거나 시각적으로 구분이 안 될 수 있음 — 검증된 이미지/라이브러리를 저장해서 재사용하는 편이 안전함
- 게임마다 같은 개념(빈 컬럼 규칙 등)이 실제로는 다를 수 있음 — 다른 카드게임을 만들 때 규칙을 그대로 복사하지 말고 해당 게임의 정식 규칙을 확인할 것
- 클론다이크 난이도는 "다시 섞기 횟수 제한"으로 어렵게 만들면 체감 난이도가 과도하게 튀어오름 — 다시 섞기는 무제한으로 두고 힌트/자동정리 유무로 난이도를 나누는 편이 나음
- 뉴스 중복 제거는 완전일치만으로는 부족함(언론사마다 제목 표현이 미묘하게 다름) — 문자 2-그램 자카드 유사도로 근사중복까지 잡아야 실제로 걸러짐(임계값 0.40, `NewsFetchService`)
- AI 브리핑류(Gemini 호출)는 짧은 주기로 재시도하면 무료 할당량이 다른 기능과 겹쳐 금방 소진됨 — 위 'Gemini API 호출 정책' 절 참고(하루 정해진 횟수만 스케줄러가 생성해 DB에 저장, 방문자 요청은 DB만 읽음)
- **`@Scheduled` 작업들은 기본적으로 스레드풀 크기 1개를 공유한다**(Spring Boot 기본값) — 스케줄 작업이 하나둘 늘어나면, 한 작업이 오래 걸릴 때 다른 작업이 실행을 못 받고 계속 밀릴 수 있다(`news_briefing`이 2주 넘게 한 번도 안 돈 원인이 이거였음). `application.yml`의 `spring.task.scheduling.pool.size`를 스케줄 작업 개수에 맞춰 늘려둘 것(현재 5)
- 자주 조회/존재확인(`existsBy...`)하는 컬럼에는 인덱스를 반드시 걸 것 — 인덱스가 없으면 테이블이 커질수록 그 쿼리가 느려지고, 그게 스케줄 작업 하나를 오래 붙잡아 다른 스케줄 작업들까지 지연시키는 연쇄 문제로 이어질 수 있다(`NewsArticle.link/title` 사례)
- O(n²)으로 커지는 로직(전체 데이터를 서로 비교하는 근사중복 정리 등)에는 상한(대상이 비정상적으로 많아지면 이번 주기는 건너뛰고 다음 주기에 재시도)을 걸어둘 것 — 안 걸어두면 데이터가 예상보다 많이 쌓였을 때 그 작업 하나가 스케줄러 스레드를 오래 붙잡는 원인이 될 수 있다
- Gemini generateContent 호출은 모델 후보 여러 개를 순서대로 시도하되, 404(모델 없음)뿐 아니라 5xx(일시 과부하)에서도 다음 후보로 넘어가야 함 — 하나만 실패해도 전체를 포기하지 않도록. 공용 `GeminiClient`(`com.jihun.portfolio.common`)를 재사용할 것, 서비스마다 새로 구현하지 말 것
- Start.sh/conf/*.conf는 레포에 없고 EC2에만 있음 — 이 파일들을 고치는 작업은 Claude가 직접 못 하고 항상 사용자에게 서버에서 할 단계를 안내해야 함
- AWS/DB처럼 "관리자에게 유용한 실시간 정보"를 보여줄 때는, 이미 갖고 있는 자격증명(EC2 메타데이터, 앱의 DataSource)으로 되는 범위부터 먼저 구현하고, 새 자격증명(IAM 액세스키 등)이 필요한 부분은 보안 영향 범위를 설명하고 사용자 확인 후에 진행할 것
- **인증 없는 공개 페이지에 자유 SQL 입력창을 둘 때는 반드시 화이트리스트로 제한할 것**: SELECT만 허용,
  금지 키워드 차단, FROM/JOIN 대상 테이블을 사전에 정해둔 목록으로만 제한. 운영 계정과 같은 커넥션을
  쓰는 이상 "느낌만 내는" 용도라도 실제 운영 테이블이 조회되지 않도록 반드시 검증 로직을 넣는다(`server-control` 사례).
  INSERT/UPDATE처럼 데이터를 바꾸는 연습 기능은 SQL 콘솔에 맡기지 말고, 별도 연습용 테이블 + 파라미터
  바인딩된 전용 엔드포인트/모달 폼으로 분리할 것(위 서버제어 메모 참고)
- **새 페이지·기능을 추가할 때마다 "콘텐츠 작성 원칙"·"모바일 최적화 원칙"·"Gemini API 호출 정책" 절을 다시 확인할 것** — 특정 기업 어필성 문구나 주관적 형용사가 섞이지 않았는지, 게임 설명이 불릿 위주로 간결한지, 모바일 폭에서 깨지는 곳은 없는지, Gemini를 방문자 요청 시점에 직접 부르고 있지는 않은지 체크

## 환경변수

**`conf/app.conf`**: `SPRING_PROFILES_ACTIVE`, `DB_HOST/NAME/USER/PASSWORD`, `GEMINI_API_KEY`, `NAVER_MAP_CLIENT_ID/SECRET`, `KAKAO_API_KEY`, `REALESTATE_API_KEY`, `TOSS_CLIENT_ID`, `TOSS_CLIENT_SECRET`

**`conf/private.conf`**(회원 인증 관련, 분리 관리): `APP_ENCRYPTION_KEY`(회원 전화번호·이메일 암호화 키이자 remember-me 서명 키, 운영에서 반드시 별도 값 지정 — 한 번 정하면 바꾸지 말 것, 바꾸면 기존 암호화된 회원정보를 못 읽음), `MAIL_HOST`/`MAIL_PORT`/`MAIL_USERNAME`/`MAIL_PASSWORD`(비밀번호 재설정 이메일 발송)

최초 관리자 계정(`simering`/`admin`)은 환경변수가 아니라 `MemberService`에 고정값으로 들어있음 — 별도 설정 불필요, 로그인 후 즉시 비밀번호 변경.

AWS Cost Explorer/Billing API용 IAM 액세스키는 아직 추가되지 않음(대시보드의 "사용량" 파트 미구현, 위 대시보드 절 참고).

## 코딩 컨벤션

- 새 기능: `com.jihun.portfolio.<기능>` + `/api/<기능>/*` + 정적 페이지/탭
- 공통 스타일 `/css/base.css`, 공통 네비 `/js/nav.js`(`NAV_ITEMS`, 드롭다운 지원) — 비공개 영역은 `/js/private-nav.js`(`PRIVATE_NAV_ITEMS`)로 별도 관리
- 다크 네이비(#0B101B)+앰버(#F2A93B) 테마
- 신규 UI는 Vue.js/React(CDN) 우선 검토
- 새 기능을 배포할 때는 처음부터 모바일 폭(360~400px) 기준으로 확인한다(위 '모바일 최적화 원칙' 참고)
- Gemini API를 쓰는 기능은 위 'Gemini API 호출 정책'을 따른다(공용 `GeminiClient` 사용, 스케줄러+DB 저장 패턴)
- 게임별 인메모리 대국상태 + 공통 `GameScore` 랭킹 패턴 유지(난이도별로 나눠야 하면 `gameType`에 접미사 부여, 예: `FREECELL_EASY`, `KLONDIKE_EASY`, `OMOK_EASY`, `SPIDER_EASY`)
- 게임 카테고리(웹게임/보드게임/카드게임)별로 통합 허브 페이지(`game.html`/`board.html`/`cardgame.html`)를 두고, 그 안에서 탭으로 개별 게임을 확장. 카드 이미지 렌더링(cardmeister)과 카드 뒷면 이미지, CSS(`.fc-*` 클래스)는 카드게임 간에 공유해서 재사용
- 설정 화면부터 랭킹보드를 상시 노출하는 2단(`.two-col`) 레이아웃을 모든 랭킹 보유 게임에 공통 적용
- 지도처럼 여러 카테고리/조건으로 확장되는 검색 기능은 "빈 값일 때의 기본값"을 함부로 하드코딩하지 말 것 —
  조건이 늘어날수록 그 기본값과 결합해 예상 못한 조합에서 실패할 수 있다(위 '지도 API 메모' 참고)
