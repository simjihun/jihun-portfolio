-- =====================================================================
-- 서버제어(servercontrol) 4차 개편 SQL
-- HeidiSQL로 RDS(MySQL)에 직접 접속해서 이 파일 전체를 실행해주세요.
--
-- INSERT/UPDATE 연습 전용 테이블 2개를 추가한다. 기존
-- mms_practice_message_log / mms_practice_server_config는 조회(SELECT)
-- 전용으로 그대로 두고, 쓰기 연습은 이 새 테이블들로만 한다.
-- =====================================================================

CREATE TABLE IF NOT EXISTS mms_practice_template (
  template_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  server_id        BIGINT NOT NULL,
  template_name    VARCHAR(50)  NOT NULL,
  template_content VARCHAR(200) NOT NULL,
  use_yn           VARCHAR(1)   NOT NULL DEFAULT 'Y',
  reg_date         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  upd_date         DATETIME NULL,
  INDEX idx_template_server (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS mms_practice_schedule (
  schedule_id      BIGINT AUTO_INCREMENT PRIMARY KEY,
  server_id        BIGINT NOT NULL,
  schedule_name    VARCHAR(50) NOT NULL,
  send_time        VARCHAR(5)  NOT NULL,               -- HH:MM
  repeat_type      VARCHAR(10) NOT NULL DEFAULT 'DAILY', -- DAILY, WEEKLY, ONCE
  status           VARCHAR(10) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PAUSED
  reg_date         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  upd_date         DATETIME NULL,
  INDEX idx_schedule_server (server_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO mms_practice_template (server_id, template_name, template_content, use_yn) VALUES
(1, '가입환영 안내', '[MMS] 가입을 환영합니다. 첫 구매 시 10% 할인 쿠폰이 발급됩니다.', 'Y'),
(1, '결제완료 안내', '[MMS] 결제가 완료되었습니다. 주문번호: {order_no}', 'Y'),
(2, '배송출발 안내', '[MMS] 상품이 발송되었습니다. 배송조회: {tracking_no}', 'Y'),
(3, '이벤트 안내', '[MMS] 여름 특가 이벤트가 시작되었습니다.', 'N');

INSERT INTO mms_practice_schedule (server_id, schedule_name, send_time, repeat_type, status) VALUES
(1, '아침 발송 배치', '09:00', 'DAILY', 'ACTIVE'),
(2, '주간 리포트 발송', '18:00', 'WEEKLY', 'ACTIVE'),
(3, '월말 정산 안내', '10:00', 'ONCE', 'PAUSED');
