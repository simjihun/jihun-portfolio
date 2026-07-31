# hunit 포트폴리오 — jihun project

> **https://hunit.kr** — 직접 만들고, 직접 배포하고, 직접 운영하는 기능들을 하나씩 쌓아가는 공간

하나의 Spring Boot 애플리케이션 안에 기능(프로젝트)별 패키지를 추가해가는 **확장형 포트폴리오 플랫폼**입니다.
모든 기능은 AWS EC2에서 실제로 동작하며, `git push` 한 번으로 자동 배포됩니다.

## 프로젝트 목록

| 상태 | 프로젝트 | 설명 |
|---|---|---|
| 🟢 LIVE | **Message (MMS)** | 이동통신 메시징 시스템 축소판 — 멀티쓰레드 워커 · 비동기 큐 · 자동 재시도 · 실시간 관제 대시보드 |
| ⚪ 예정 | 웹 게임 | 브라우저 미니 게임 + 랭킹 |
| ⚪ 예정 | 지도 연동 | 카카오맵/네이버맵 API 활용 |
| ⚪ 예정 | 가격 비교 | 상품 링크 기반 가격 비교 |
| ⚪ 예정 | AI 뉴스 요약 | 주식·경제 뉴스 수집 + AI 요약 |

## Message (MMS) 아키텍처

```
[관제 콘솔 / REST API]
        │ 접수 즉시 응답 (비동기)
        ▼
   DB 저장(PENDING) ──▶ [MessageQueue · BlockingQueue]
                              │
              ┌─────────────┼────────────┐
         [worker-1]      [worker-2]     [worker-3]
              └── 발송 시뮬레이션 → 성공(SENT) / 재시도 재큐잉 / 최종실패(FAILED)
```

## 기술 스택 & 인프라

- **Backend**: Java 21, Spring Boot 3.5 (Web, Data JPA, Validation), Logback
- **DB**: MySQL on AWS RDS (운영 `prod` 프로파일) / H2 (로컬)
- **Frontend**: Vanilla JS + Chart.js
- **Infra**: AWS EC2 (Amazon Linux 2023) · nginx 리버스 프록시 · Let's Encrypt HTTPS · 탄력적 IP · 가비아 DNS
- **CI/CD**: GitHub Actions — push → 빌드 → EC2 전송 → 재시작 → `/healthz` 검증

## 패키지 구조 (기능별 확장)

```
com.jihun.portfolio
├── common/          # 공통 (헬스체크 등)
├── message/         # Message (MMS) 프로젝트
│   ├── controller/  #   REST API (/api/message/*)
│   ├── domain/      #   엔티티·상태
│   ├── queue/       #   발송 대기열
│   ├── repository/  #   JPA 저장소
│   └── worker/      #   멀티쓰레드 발송 데몬
├── game/            # (예정)
└── ...
```

새 기능은 하위 패키지 + `/api/<기능>/*` API + 정적 페이지 한 장을 추가하는 방식으로 확장합니다.

## 실행

```bash
# 로컬 개발 (H2, 포트 8081)
mvn spring-boot:run

# 빌드
mvn clean package
```

운영 설정은 코드가 아닌 서버의 `conf/app.conf`에서 환경변수로 주입됩니다.
(`SPRING_PROFILES_ACTIVE=prod` 설정 시 RDS로, 미설정 시 H2로 동작)

### EC2 운영 구조

```
/home/jihun/
├─ Start.sh / killall.sh        # 실행·종료 (Graceful Shutdown)
├─ conf/app.conf                # 환경 설정 (포트·DB)
├─ libs/jihun-portfolio-*.jar
└─ logs/app.log                 # 당일 로그 (자정에 날짜별 자동 보관, 14일 보존)
```
