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

## 메뉴/메인화면 순서 규칙 (중요)

메뉴·메인화면 기능 섹션 순서 고정: **AI 뉴스 → AI 주식 → 지도 API → MMS → 웹 게임**.
새 기능 배포 시 `index.html` 기능 섹션과 `nav.js`의 `NAV_ITEMS`에 이 순서로 동시 추가한다.

## 기능별 현황 (2026-08-05)

| 기능 | 경로 | 상태 | 비고 |
|---|---|---|---|
| AI 뉴스 | `/news` | LIVE | 다중언론사 RSS, Gemini 매일 07시 브리핑, 제목 완전일치 중복 제거, 주요뉴스 카테고리별 최소 1건+이미지 |
| AI 주식 | `/stock` | LIVE | 토스증권 Open API+업비트+Gemini. 상세는 아래 참고 |
| 지도 API | `/map` | LIVE | 맛집(카카오)+부동산(국토부) |
| MMS | `/mms` | LIVE | 워커 5개, 다중발송, 자정 초기화 |
| 웹 게임 | `/game` | LIVE(오목·야구·장기) | 장기: 알파베타 완전탐색 AI, 마상 포진 4종, 초 선공, move/ai-move 분리로 장군표시 확보 |
| 체스, 가격비교 | - | 미착수 | |

## AI 주식 메모 (`/stock`)

- `com.jihun.portfolio.stock`: `TossApiClient`(OAuth+전역 스로틀+429재시도), `StockDashboardService`(TTL캐시), `StockController`
- 스키마 함정: RankingItem엔 종목명/시총 없음(종목마스터 조인), changeRate는 소수비율(×100 필요), exchange-rate는 baseCurrency/quoteCurrency 필수+등락률 자체계산, 캘린더 12h 캐시+별도 엔드포인트
- 프론트: 랭킹 테이블은 폴링마다 지우지 않고 심볼키로 재사용+FLIP 재정렬+변경셀 플래시. 국기는 flagcdn.com 이미지(이모지는 Windows에서 깨짐)
- 서버설정 필요: `TOSS_CLIENT_ID/SECRET`, 토스 허용IP 등록

## 문서·페이지 문구 원칙 (중요)

- **기능 페이지 제목 아래**는 설명 문단 대신 스킬칩(`.skill-row`)으로 기술스택 표기.
- **메인화면(index.html)** 각 섹션도 `.skills` 칩 + `<ul>` 불릿만 사용.
- **README.md**와 **개발 중인 포트폴리오 사이트(모든 페이지)** 모두 사용자와 나눈 주관적 대화·개인적 서사·감정 표현을 배제하고, **적용된 기술 스킬과 사용한 알고리즘 중심**으로 객관적·기술적으로 작성한다. ("직접 만들고 쌓아가는 공간" 같은 감성적 문구 금지 — 사용자가 명시적으로 싫어함.)

## 프론트 프레임워크 원칙 (2026-08-05)

신규 기능은 순수 JS보다 Vue.js/React(CDN, 빌드 없이) 우선 검토. 기존 페이지 통째 마이그레이션은 불필요, 신규 단위로 점진 도입.

## 장기 구현 참고

- 궁·사·차·포는 궁성 대각선 지원(포는 중앙 기물을 넘어야 함), 병만 간소화로 생략
- AI는 알파베타 완전탐색(EASY/MEDIUM 2플라이/HARD 4플라이), 후보를 자르지 않고 정렬만 함
- `move()`는 사람 수만 반영, `aiMove()` 별도 엔드포인트(프론트 1초 지연 후 호출)로 장군표시 확보
- 보드는 격자 전체를 하나의 SVG로(경계 삐져나옴 방지), 장군 시 보드발광+궁칸링+배지 3중 표시

## 최근 실수/함정

- MOLIT API 신주소 `apis.data.go.kr`, 네이버 지오코딩 대신 카카오 키워드검색, 카카오 `pageable_count` 사용, 현위치검색엔 `x,y,radius,sort=distance` 필수
- 외부 API는 반드시 타임아웃 명시
- SVG `vector-effect="non-scaling-stroke"`는 얇은 선을 사실상 안 보이게 만듦 — 빼야 함
- 이모지 국기는 Windows에서 깨짐 → flagcdn.com 이미지 사용
- 실시간 테이블은 innerHTML 통째로 갈아엎지 말고 키 기반 재사용+부분갱신
- GitHub 파일 편집 도구가 타임아웃 에러를 내도 실제로는 성공한 경우가 있음 — 재시도 전 `get_file_contents`로 확인, sha는 항상 직전에 새로 조회

## 환경변수

`SPRING_PROFILES_ACTIVE`, `DB_HOST/NAME/USER/PASSWORD`, `GEMINI_API_KEY`, `NAVER_MAP_CLIENT_ID/SECRET`, `KAKAO_API_KEY`, `REALESTATE_API_KEY`, `TOSS_CLIENT_ID`, `TOSS_CLIENT_SECRET`

## 코딩 컨벤션

- 새 기능: `com.jihun.portfolio.<기능>` + `/api/<기능>/*` + 정적 페이지/탭
- 공통 스타일 `/css/base.css`, 공통 네비 `/js/nav.js`(`NAV_ITEMS`)
- 다크 네이비(#0B101B)+앰버(#F2A93B) 테마
- 신규 UI는 Vue.js/React(CDN) 우선 검토
- 게임별 인메모리 대국상태 + 공통 `GameScore` 랭킹 패턴 유지
