# hunit 포트폴리오 — jihun project

> **https://hunit.kr** — 직접 만들고, 직접 배포하고, 직접 운영하는 기능들을 하나씩 쌓아가는 공간

하나의 Spring Boot 애플리케이션 안에 기능(프로젝트)별 패키지를 추가해가는 **확장형 포트폴리오 플랫폼**입니다.
모든 기능은 AWS EC2에서 실제로 동작하며, `git push` 한 번으로 자동 배포됩니다.

## 프로젝트 목록

| 상태 | 프로젝트 | 설명 |
|---|---|---|
| 🟢 LIVE | **MMS** | 이동통신 메시징 시스템 축소판 — 멀티쓰레드 워커(5개) · 비동기 큐 · 자동 재시도 · 최대 1000건 다중 발송 · 실시간 관제 대시보드 · 매일 자정 이력 초기화 |
| 🟢 LIVE | **AI 뉴스** | RSS 다중 언론사(한국경제·SBS·동아일보·매일경제) 자동 수집 · 속보/주요뉴스 하이라이트 · Gemini AI 매일 3줄 브리핑 · OpenGraph 기사 미리보기 |
| 🟢 LIVE | **지도 API** | 맛집 지도(카카오 Local API + 네이버 지도, 현위치·재검색) / 부동산 시세(국토부 실거래가 API, 단지별 좌표·가격 지도 표시) |
| ⚪ 예정 | 웹 게임 | 브라우저 미니 게임 + 랭킹 |
| ⚪ 예정 | 가격 비교 | 상품 링크 기반 가격 비교 |

## 아키텍처 요약

### MMS — 비동기 큐 + 멀티쓰레드 워커

```
[관제 콘솔 / REST API]
        │ 접수 즉시 응답 (비동기)
        ▼
   DB 저장(PENDING) ──▶ [MessageQueue · BlockingQueue]
                              │
        ┌──────────┬──────────┼──────────┬──────────┐
   [worker-1]  [worker-2]  [worker-3]  [worker-4]  [worker-5]
        └── 발송 시뮬레이션 → 성공(SENT) / 재시도 재큐잉 / 최종실패(FAILED)

매일 00:00(Asia/Seoul) 발송 이력 자동 초기화 (서버 시작 시에는 초기화하지 않음)
```

### AI 뉴스 — RSS 수집 + AI 요약

```
[NewsFetchService] 30분마다 카테고리별 다중 언론사 RSS 수집 (소스 단위 장애 격리, 3일 지난 기사 자동 정리)
[NewsBriefingService] 매일 07:00 카테고리별 헤드라인을 Gemini에 전달해 3줄 브리핑 생성 (무료 한도 내 안정 운영)
[NewsPreviewService] 기사 클릭 시 OpenGraph 메타(이미지·제목·요약)만 가져와 팝업 표시 — 본문은 긁지 않음(저작권 안전)
```

### 지도 API — 맛집 지도 + 부동산 시세

```
맛집 지도: 카카오 Local API(키워드/현위치 검색) + 네이버 지도(마커 표시)
부동산 시세: 국토부 실거래가 API(XML) + 카카오 키워드검색(단지 좌표 지오코딩) + 네이버 지도
두 지도는 완전히 분리된 인스턴스로 동작 — 뷰포트·검색결과를 localStorage에 각각 기억해
    탭을 옮기거나 재방문해도 마지막으로 보던 화면이 그대로 복원된다.
```

## 기술 스택 & 인프라

- **Backend**: Java 21, Spring Boot 3.5 (Web, Data JPA, Validation), Logback
- **DB**: MySQL on AWS RDS (운영 `prod` 프로파일) / H2 (로컬)
- **외부 API**: 카카오 Local API, 네이버 지도(Maps JS), 국토교통부 실거래가 OpenAPI, Google Gemini, RSS(ROME)
- **Frontend**: Vanilla JS + Chart.js, 공통 상단 네비게이션(`nav.js`) + 반응형 레이아웃
- **Infra**: AWS EC2 (Amazon Linux 2023) · nginx 리버스 프록시 · Let's Encrypt HTTPS · 탄력적 IP · 가비아 DNS
- **CI/CD**: GitHub Actions — push → 빌드 → EC2 전송 → 재시작 → `/healthz` 검증

## 패키지 구조 (기능별 확장)

```
com.jihun.portfolio
├── common/          # 공통 (헬스체크, URL 라우팅)
├── message/         # MMS
│   ├── controller/  #   REST API (/api/message/*)
│   ├── domain/      #   엔티티·상태
│   ├── queue/       #   발송 대기열
│   ├── repository/  #   JPA 저장소
│   ├── scheduler/   #   자정 이력 초기화
│   └── worker/      #   멀티쓰레드 발송 데몬
├── news/            # AI 뉴스
│   ├── controller/  #   REST API (/api/news/*)
│   ├── domain/      #   기사·브리핑 엔티티
│   ├── repository/  #   JPA 저장소
│   └── service/      #   RSS 수집 · Gemini 브리핑 · 기사 미리보기
├── map/             # 지도 API
│   ├── MapController.java          # 맛집 검색(카카오) · 설정 · 헬스체크
│   ├── RealEstateController.java   # 부동산 실거래 조회(국토부)
│   ├── KakaoGeocodeService.java    # 아파트 단지 좌표 지오코딩
│   └── RealEstateRegions.java      # 조회 가능 지역 목록
├── game/            # (예정)
└── ...
```

새 기능은 하위 패키지 + `/api/<기능>/*` API + 정적 페이지(+공통 `nav.js`에 메뉴 항목) 추가 방식으로 확장합니다.

## 실행

```bash
# 로컬 개발 (H2, 포트 8081)
mvn spring-boot:run

# 빌드
mvn clean package
```

운영 설정은 코드가 아닌 서버의 `conf/app.conf`에서 환경변수로 주입됩니다.

| 환경변수 | 용도 |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | 설정 시 RDS(MySQL), 미설정 시 H2로 동작 |
| `DB_HOST` / `DB_NAME` / `DB_USER` / `DB_PASSWORD` | RDS 접속 정보 |
| `GEMINI_API_KEY` | AI 뉴스 3줄 브리핑 (미설정 시 기능만 비활성화) |
| `KAKAO_API_KEY` | 맛집 검색 + 부동산 좌표 지오코딩 |
| `NAVER_MAP_CLIENT_ID` | 지도 표시(Maps JS) |
| `REALESTATE_API_KEY` | 국토부 실거래가 조회 |

### EC2 운영 구조

```
/home/jihun/
├─ Start.sh / killall.sh        # 실행·종료 (Graceful Shutdown)
├─ conf/app.conf                # 환경 설정 (포트·DB·외부 API 키)
├─ libs/jihun-portfolio-*.jar
└─ logs/app.log                 # 당일 로그 (자정에 날짜별 자동 보관, 14일 보존)
```
