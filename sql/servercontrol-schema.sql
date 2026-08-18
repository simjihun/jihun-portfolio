-- =====================================================================
-- 서버제어(servercontrol) 연습 기능 — DB 스키마 + 더미 데이터
--
-- 이 파일은 애플리케이션이 자동으로 실행하지 않습니다(JPA 엔티티가 아니라
-- JdbcTemplate로 직접 SQL을 짜는 연습이라 auto-ddl 대상이 아님).
-- HeidiSQL로 RDS(MySQL)에 직접 접속해서 이 파일 전체를 실행해주세요.
-- 로컬 H2로 테스트할 때도 동일한 문법이 대부분 통하지만, ENGINE=InnoDB 줄만
-- 에러가 나면 그 줄을 지우고 실행하면 됩니다.
-- =====================================================================

-- 1) 서버 마스터 (5대 이상의 가상 서버)
CREATE TABLE IF NOT EXISTS admin_servers (
  server_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  server_name   VARCHAR(50)  NOT NULL,
  server_alias  VARCHAR(20)  NOT NULL,
  host_ip       VARCHAR(50)  NOT NULL,
  ssh_port      INT          NOT NULL DEFAULT 22,
  os_type       VARCHAR(30)  NOT NULL,
  env_type      VARCHAR(20)  NOT NULL,
  status        VARCHAR(10)  NOT NULL DEFAULT 'RUNNING',
  reg_date      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 2) 서버별 리소스 지표 이력 (CPU/MEM/DISK) — 실시간 차트용
CREATE TABLE IF NOT EXISTS admin_server_metric (
  metric_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
  server_id   BIGINT NOT NULL,
  cpu_usage   DECIMAL(5,2) NOT NULL,
  mem_usage   DECIMAL(5,2) NOT NULL,
  disk_usage  DECIMAL(5,2) NOT NULL,
  checked_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_metric_server_checked (server_id, checked_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) 서버 제어(시작/중지/재시작) 이력
CREATE TABLE IF NOT EXISTS admin_server_control_log (
  log_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
  server_id     BIGINT NOT NULL,
  action_type   VARCHAR(20) NOT NULL,
  action_result VARCHAR(10) NOT NULL,
  requested_by  VARCHAR(30) NOT NULL DEFAULT 'admin',
  requested_at  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_log_server_requested (server_id, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 4) DB CRUD(select/insert/update/delete) 연습용 더미 테이블 — 이 기능과만 사용, 다른 기능과 무관
CREATE TABLE IF NOT EXISTS practice_dummy_table (
  record_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
  record_name   VARCHAR(50) NOT NULL,
  record_email  VARCHAR(100),
  record_status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE',
  reg_date      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  upd_date      DATETIME NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ===== 더미 서버 6대 =====
INSERT INTO admin_servers (server_name, server_alias, host_ip, ssh_port, os_type, env_type, status) VALUES
('WEB 서버 1호기', 'WEB-01',   '10.0.1.11', 22, 'CentOS 7',      'PROD', 'RUNNING'),
('WEB 서버 2호기', 'WEB-02',   '10.0.1.12', 22, 'CentOS 7',      'PROD', 'RUNNING'),
('WAS 서버 1호기', 'WAS-01',   '10.0.2.11', 22, 'Ubuntu 18.04',  'PROD', 'RUNNING'),
('WAS 서버 2호기', 'WAS-02',   '10.0.2.12', 22, 'Ubuntu 18.04',  'PROD', 'STOPPED'),
('DB 서버 1호기',  'DB-01',    '10.0.3.11', 22, 'CentOS 7',      'PROD', 'RUNNING'),
('배치 서버 1호기', 'BATCH-01', '10.0.4.11', 22, 'Ubuntu 20.04', 'STG',  'RUNNING');

-- ===== DB CRUD 연습용 더미 레코드 =====
INSERT INTO practice_dummy_table (record_name, record_email, record_status) VALUES
('김철수', 'chulsoo@test.com', 'ACTIVE'),
('이영희', 'younghee@test.com', 'ACTIVE'),
('박민수', 'minsoo@test.com', 'INACTIVE'),
('정하나', 'hana@test.com', 'ACTIVE');
