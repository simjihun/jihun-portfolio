-- =====================================================================
-- 서버제어(servercontrol) 기능 — 2차 개편 SQL
-- HeidiSQL로 RDS(MySQL)에 직접 접속해서 이 파일 전체를 실행해주세요.
-- (이전에 실행한 sql/servercontrol-schema.sql이 이미 적용되어 있어야 합니다)
-- =====================================================================

-- 1) 서버 명칭을 MMS 발송 인프라 구성으로 변경
UPDATE admin_servers SET server_name='MMS 발송 웹서버 1호기',   server_alias='MMS-WEB-01'   WHERE server_alias='WEB-01';
UPDATE admin_servers SET server_name='MMS 발송 웹서버 2호기',   server_alias='MMS-WEB-02'   WHERE server_alias='WEB-02';
UPDATE admin_servers SET server_name='MMS 발송 처리서버 1호기', server_alias='MMS-WAS-01'   WHERE server_alias='WAS-01';
UPDATE admin_servers SET server_name='MMS 발송 처리서버 2호기', server_alias='MMS-WAS-02'   WHERE server_alias='WAS-02';
UPDATE admin_servers SET server_name='MMS DB서버 1호기',        server_alias='MMS-DB-01'    WHERE server_alias='DB-01';
UPDATE admin_servers SET server_name='MMS 정산배치 서버 1호기', server_alias='MMS-BATCH-01' WHERE server_alias='BATCH-01';

-- 2) SQL 콘솔 실습용 테이블 (화이트리스트에 등록된 테이블만 이 두 개)
CREATE TABLE IF NOT EXISTS mms_practice_message_log (
  log_id       BIGINT AUTO_INCREMENT PRIMARY KEY,
  server_id    BIGINT NOT NULL,
  phone_number VARCHAR(20)  NOT NULL,
  msg_type     VARCHAR(10)  NOT NULL, -- SMS, LMS, MMS
  send_status  VARCHAR(10)  NOT NULL, -- SUCCESS, FAIL, PENDING
  sent_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  INDEX idx_msglog_server (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mms_practice_server_config (
  config_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
  server_id    BIGINT NOT NULL,
  config_key   VARCHAR(50) NOT NULL,
  config_value VARCHAR(100) NOT NULL,
  INDEX idx_config_server (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 3) 더미 발송 로그 (서버별로 몇 건씩)
INSERT INTO mms_practice_message_log (server_id, phone_number, msg_type, send_status) VALUES
(1, '010-12**-56**', 'MMS', 'SUCCESS'),
(1, '010-23**-78**', 'SMS', 'SUCCESS'),
(1, '010-34**-90**', 'MMS', 'FAIL'),
(2, '010-45**-12**', 'LMS', 'SUCCESS'),
(2, '010-56**-34**', 'MMS', 'SUCCESS'),
(3, '010-67**-56**', 'SMS', 'PENDING'),
(3, '010-78**-90**', 'MMS', 'SUCCESS'),
(4, '010-89**-12**', 'LMS', 'FAIL'),
(5, '010-90**-34**', 'MMS', 'SUCCESS'),
(6, '010-11**-22**', 'SMS', 'SUCCESS');

-- 4) 더미 서버 설정값
INSERT INTO mms_practice_server_config (server_id, config_key, config_value) VALUES
(1, 'max_thread_pool', '50'),
(1, 'retry_count', '2'),
(2, 'max_thread_pool', '50'),
(3, 'max_thread_pool', '80'),
(3, 'retry_count', '3'),
(4, 'max_thread_pool', '80'),
(5, 'connection_pool_size', '20'),
(6, 'batch_interval_min', '10');

-- 5) 기존 practice_dummy_table은 더 이상 사용하지 않음(필요 시 직접 DROP)
-- DROP TABLE IF EXISTS practice_dummy_table;
