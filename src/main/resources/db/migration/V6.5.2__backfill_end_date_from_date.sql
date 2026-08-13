-- V6.5.1이 end_date를 NULL만 넣고 기존 행을 백필하지 않았다. 안 채우면 이미 존재하던
-- Todo는 end_date가 비어 조회 시(overlap 쿼리) 통째로 안 잡힌다.
UPDATE `personal_todo` SET `end_date` = `date` WHERE `end_date` IS NULL;
