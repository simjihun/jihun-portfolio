# hunit 포트폴리오 — jihun-portfolio

**https://hunit.kr**

Spring Boot 애플리케이션 하나에 기능별 패키지를 추가하는 구조. 모든 기능은 AWS EC2에서 동작하며 `main` 브랜치 push 시 GitHub Actions로 자동 빌드·배포된다.

## 프로젝트 목록

| 상태 | 프로젝트 | 적용 기술·알고리즘 |
|---|---|---|
| 🟢 LIVE | **AI 뉴스** | RSS 다중 언론사 자동 수집(ROME), 링크·제목 완전일치 + 문자 2-그램 유사도 기반 근사중복 제거, Gemini API 일일 브리핑 생성(스케줄러+DB 저장, 배포 직후엔 즉시 1회), OpenGraph 메타데이터 파싱(JSoup) 기반 기사 미리보기 |
| 🟢 LIVE | **AI 주식** | 토스증권 Open API(OAuth2 Client Credentials, 전역 요청 스로틀링, TTL 캐시), 업비트 공개 API, Gemini API 시황 요약(하루 3회 스케줄러+DB 저장), 프론트엔드 FLIP 애니메이션 기반 실시간 테이블 갱신 |
| 🟢 LIVE | **지도 API** | 카카오 Local API(키워드·반경 검색), 네이버 지도 SDK, 국토교통부 실거래가 공공데이터 API 연동 |
| 🟢 LIVE | **MMS** | 비동기 큐 + 멀티쓰레드 워커 풀(5개), 재시도 로직, 실시간 처리량 집계 |
| 🟢 LIVE | **웹게임** | 숫자야구(스트라이크/볼 판정), 발리볼(Canvas 기반 실시간 물리 시뮬레이션) |
| 🟢 LIVE | **보드게임** | 오목(미니맥스 탐색 AI), 장기(알파베타 가지치기 완전탐색 AI, 궁성 대각선 이동 규칙) |
| 🟢 LIVE | **카드게임** | 프리셀(서플무브 용량 계산), 클론다이크(점수 기반 힌트 탐색), 스파이더(무늬 수 기반 난이도) |
| ⚪ 예정 | 체스 | 규칙 엔진 + AI 탐색 |
| ⚪ 예정 | 가격 비교 | 상품 링크 기반 가격 비교 |

## 아키텍처 요약

### MMS — 비동기 큐 + 멀티쓰레드 워커

```
[REST API] 접수 즉시 응답(비동기)
        ▼
  DB 저장(PENDING) ──▶ [MessageQueue · BlockingQueue]
                              │
        ┌──────────┬──────────┼──────────┬──────────┐
   [worker-1]  [worker-2]  [worker-3]  [worker-4]  [worker-5]
        └── 발송 처리 → 성공(SENT) / 재시도 재큐잉 / 최종실패(FAILED)

매일 00:00(Asia/Seoul) 발송 이력 자동 초기화
```

### AI 뉴스 — RSS 수집 + AI 요약

```
[NewsFetchService] 30분 주기, 카테고리별 다중 언론사 RSS 수집
    - 소스 단위 장애 격리(하나가 실패해도 나머지 수집 계속)
    - 링크·제목 완전일치 중복 제거 + 문자 2-그램 자카드 유사도 기반 근사중복 제거
    - 3일 경과 기사 자동 삭제
[NewsBriefingService] 매일 07:00(+배포 직후 데이터가 없으면 즉시 1회) 카테고리별 헤드라인을
    공용 GeminiClient에 전달해 요약 생성 후 DB에 저장, 방문자 요청은 저장된 값만 조회
[NewsPreviewService] OpenGraph 메타데이터(이미지·제목·요약)만 파싱해 표시, 본문 미저장
[NewsController] 주요 뉴스는 카테고리별 최소 1건을 우선 확보한 뒤 최신순으로 채우는
    균형 배치 알고리즘 적용
```

### AI 주식 — 외부 시세 API + AI 시황 요약

```
[TossApiClient] OAuth2 Client Credentials 토큰 캐싱, 전역 300ms 요청 스로틀,
    429 응답 시 Retry-After 기반 재시도
[StockDashboardService] 지표(60s)·랭킹(60s)·수급(10m)·캘린더(12h) TTL 캐시로 외부 API
    호출량 제한. 종목마스터 조인으로 랭킹에 종목명·시가총액 부여. 환율 등락률은 당일
    최초 샘플 대비로 서버가 직접 산출. 검색 결과의 종목별 시세 조회는 전용 스레드풀로
    병렬 디스패치해 응답 대기 시간 단축
[StockAiBriefing] 시황 요약·주목종목·체크포인트는 하루 3회(09/15/21시) 스케줄러가
    공용 GeminiClient로 생성해 DB에 저장, 방문자 요청은 저장된 값만 조회
[Upbit 공개 API] 비트코인 시세·캔들 조회
[프론트엔드] 실시간 랭킹 테이블은 폴링마다 DOM을 재생성하지 않고 키 기반으로 기존
    엘리먼트를 재사용, FLIP 기법으로 순위 변동을 애니메이션 처리
```

### 웹게임 — 숫자야구·발리볼 (`/game`)

```
[숫자야구] 스트라이크/볼 판정 로직, 시도 횟수·소요시간 기준 랭킹
[발리볼] 중력·충돌·반사·캐릭터 이동을 포함한 물리 시뮬레이션을 Canvas +
    requestAnimationFrame으로 프론트엔드에서 전부 처리, 서버는 대결 결과만 저장.
    AI는 공의 궤적을 프레임 단위로 시뮬레이션해 낙하 지점을 예측
```

### 보드게임 — 오목·장기 (`/game/board`)

```
[오목] 미니맥스 탐색 기반 AI, 난이도별 탐색 깊이 조절.
    승리한 대국만 서버가 검증 후 착수 수·소요시간을 랭킹에 기록
[장기] 알파베타 가지치기를 적용한 완전탐색 AI(중급 2플라이 / 고급 4플라이)
    - 기물별 이동 규칙, 궁성 대각선 이동(포는 스크린 기물을 넘어야 이동 가능)
    - 마·상 포진 4종 선택 가능
    - 장군 판정을 사람 수 처리와 AI 응수 처리로 분리한 API 설계
```

### 카드게임 — 프리셀·클론다이크·스파이더 (`/game/card`)

```
[공통] 뭉치 이동 유효성, 승리·막힘 판정을 전부 프론트엔드에서 계산, 서버는 결과만 저장.
    카드 이미지는 자유 라이선스(Unlicense) SVG 렌더링 라이브러리를 자체 저장해 사용

[프리셀] 서플무브 용량 = (빈 오픈칸 수+1) × 2^빈 컬럼 수, 완성 칸 안전 자동정리,
    난이도는 초기 카드 배치로만 구분

[클론다이크] 스톡→웨이스트 넘기기 방식, 완성 칸→줄 스택 되돌리기 지원.
    힌트는 가능한 모든 수를 점수화(완성 칸行 > 뒷면 카드 노출 > 빈 컬럼 확보 >
    웨이스트 이동 > 그 외)해 최고점 수를 안내

[스파이더] 104장(2벌)을 10개 컬럼에 배치, 같은 무늬로 연결된 뭉치만 이동 가능,
    K→A 같은 무늬 13장 연결 시 자동 완성. 난이도는 사용 무늬 수(1/2/4종)로 구분
```

### 지도 API — 맛집 지도 + 부동산 시세

```
맛집 지도: 카카오 Local API(키워드·현위치 반경 검색) + 네이버 지도 SDK 마커 표시
부동산 시세: 국토교통부 실거래가 공공데이터 API(XML 파싱) + 카카오 키워드검색
    기반 좌표 지오코딩 + 네이버 지도 표시
두 지도는 독립된 인스턴스로 동작하며 뷰포트·검색 상태를 localStorage에 유지
```

## 기술 스택 & 인프라

- **Backend**: Java 21, Spring Boot 3.5(Web, Data JPA, Validation, Scheduler), Logback
- **DB**: MySQL(AWS RDS, `prod` 프로파일) / H2(로컬)
- **외부 API**: 토스증권 Open API, 카카오 Local API, 네이버 지도 SDK, 국토교통부 실거래가 공공데이터 API, Google Gemini API, 업비트 공개 API, RSS(ROME)
- **Frontend**: Vanilla JS + Chart.js(신규 기능은 Vue.js/React CDN 방식 우선 검토), Web Components(cardmeister 카드 렌더링), 공통 네비게이션(`nav.js`)
- **Infra**: AWS EC2(Amazon Linux 2023), nginx 리버스 프록시, Let's Encrypt HTTPS
- **CI/CD**: GitHub Actions — push → 빌드 → EC2 배포 → 헬스체크
- **개발 환경**: Eclipse IDE 2025-03(4.35.0)

## 패키지 구조

```
com.jihun.portfolio
├── common/          # 공통(헬스체크, URL 라우팅 - WebConfig, Gemini API 공용 호출 클라이언트 - GeminiClient)
├── message/         # MMS
│   ├── controller/  #   REST API (/api/message/*)
│   ├── domain/
│   ├── queue/
│   ├── repository/
│   ├── scheduler/
│   └── worker/
├── news/            # AI 뉴스
│   ├── controller/  #   REST API (/api/news/*)
│   ├── domain/
│   ├── repository/
│   └── service/     #   RSS 수집 · Gemini 브리핑 · 기사 미리보기
├── stock/           # AI 주식
│   ├── TossApiClient.java              # 토스증권 Open API OAuth 클라이언트
│   ├── StockDashboardService.java      # 지표·랭킹·수급·캘린더·AI브리핑
│   ├── StockAiBriefing.java            # AI 브리핑 스냅샷 엔티티(단일 row)
│   ├── StockAiBriefingRepository.java
│   └── StockController.java            # REST API (/api/stock/*)
├── map/             # 지도 API
│   ├── MapController.java          # 맛집 검색(카카오)
│   ├── RealEstateController.java   # 부동산 실거래 조회(국토부)
│   ├── KakaoGeocodeService.java    # 좌표 지오코딩
│   └── RealEstateRegions.java
└── game/            # 웹게임·보드게임·카드게임 (공통 GameScore 랭킹 엔티티 공유)
    ├── domain/GameScore.java       # 게임 공용 랭킹 엔티티(gameType으로 구분)
    ├── repository/GameScoreRepository.java
    ├── omok/        # 오목 규칙·AI·랭킹 API
    ├── baseball/    # 숫자야구
    ├── janggi/      # 장기 규칙·AI(JanggiRules/JanggiAiService/JanggiService/JanggiController)
    ├── volleyball/  # 발리볼 랭킹 API(물리는 프론트엔드에서 전부 처리)
    ├── freecell/    # 프리셀 랭킹 API(카드 로직은 프론트엔드에서 전부 처리)
    ├── klondike/    # 클론다이크 랭킹 API(동일)
    └── spider/      # 스파이더 랭킹 API(동일)
```

새 기능은 하위 패키지 + `/api/<기능>/*` REST API + 정적 페이지(공통 `nav.js` 메뉴 등록) 방식으로 확장한다.
정적 페이지: `game.html`(숫자야구·발리볼), `board.html`(오목·장기), `cardgame.html`(프리셀·클론다이크·스파이더).

## 실행

```bash
# 로컬 개발 (H2, 포트 8081)
mvn spring-boot:run

# 빌드
mvn clean package
```

운영 설정은 코드가 아닌 서버의 `conf/app.conf`에서 환경변수로 주입된다.

| 환경변수 | 용도 |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | 설정 시 RDS(MySQL), 미설정 시 H2로 동작 |
| `DB_HOST` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | RDS 접속 정보 |
| `GEMINI_API_KEY` | AI 뉴스·AI 주식 브리핑 생성(미설정 시 해당 기능만 비활성화) |
| `KAKAO_API_KEY` | 맛집 검색, 부동산 좌표 지오코딩 |
| `NAVER_MAP_CLIENT_ID` | 지도 표시(Maps JS) |
| `REALESTATE_API_KEY` | 국토부 실거래가 조회 |
| `TOSS_CLIENT_ID` / `TOSS_CLIENT_SECRET` | AI 주식 시세·랭킹 조회(허용 IP 등록 필요) |

### EC2 운영 구조

```
/home/jihun/
├─ Start.sh / killall.sh        # 실행·종료
├─ conf/app.conf                # 환경 설정(포트·DB·외부 API 키)
├─ libs/jihun-portfolio-*.jar
└─ logs/app.log                 # 로그(자정에 날짜별 보관, 14일 보존)
```
