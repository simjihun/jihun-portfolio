-- =====================================================================
-- 서버제어(servercontrol) 3차 개편 SQL
-- HeidiSQL로 RDS(MySQL)에 직접 접속해서 이 파일 전체를 실행해주세요.
-- (이전 servercontrol-schema.sql, servercontrol-schema-v2.sql이 이미 적용되어 있어야 합니다)
--
-- 역할별 명칭(WEB/WAS/DB/BATCH)을 없애고, 동일한 서버 5대(서버#1~서버#5)로 단순화합니다.
-- =====================================================================

UPDATE admin_servers SET server_name='서버#1', server_alias='SRV-01' WHERE server_alias='MMS-WEB-01';
UPDATE admin_servers SET server_name='서버#2', server_alias='SRV-02' WHERE server_alias='MMS-WEB-02';
UPDATE admin_servers SET server_name='서버#3', server_alias='SRV-03' WHERE server_alias='MMS-WAS-01';
UPDATE admin_servers SET server_name='서버#4', server_alias='SRV-04' WHERE server_alias='MMS-WAS-02';
UPDATE admin_servers SET server_name='서버#5', server_alias='SRV-05' WHERE server_alias='MMS-DB-01';

-- 6번째(예전 배치 서버)는 더 이상 쓰지 않으므로 지표·이력·실습데이터까지 함께 정리 후 삭제
DELETE FROM admin_server_metric        WHERE server_id = (SELECT server_id FROM admin_servers WHERE server_alias='MMS-BATCH-01');
DELETE FROM admin_server_control_log   WHERE server_id = (SELECT server_id FROM admin_servers WHERE server_alias='MMS-BATCH-01');
DELETE FROM mms_practice_message_log   WHERE server_id = (SELECT server_id FROM admin_servers WHERE server_alias='MMS-BATCH-01');
DELETE FROM mms_practice_server_config WHERE server_id = (SELECT server_id FROM admin_servers WHERE server_alias='MMS-BATCH-01');
DELETE FROM admin_servers WHERE server_alias='MMS-BATCH-01';
