-- =====================================================================
-- V4.2.1 : recording 테이블에 "회의당 1녹음" UNIQUE 제약 추가 (김현지 V4.x 레인)
-- ---------------------------------------------------------------------
-- 수동 업로드(CAP-10) 중복 제출 방지의 최종 방어선. 애플리케이션의 existsByMeetingId 선검사는
-- TOCTOU(확인~저장 사이 경쟁)라 동시 요청에 recording 행이 두 개 생길 수 있어, DB UNIQUE로
-- 물리적으로 회의당 1행만 허용한다(recording_part의 UNIQUE와 동일 취지 — 앱 if가 아니라 DB가 최종 보장).
--
-- recording은 V1 baseline 테이블이지만, 규칙(§2·§7: recording은 공용 테이블 목록 밖)에 따라 소유 레인
-- (V4.x)에서 ALTER로 진화시킨다. 조립/수동 업로드 경로가 아직 recording에 행을 쓰기 전이라 기존 중복은 없다.
-- =====================================================================

ALTER TABLE `recording` ADD CONSTRAINT `UK_RECORDING_MEETING` UNIQUE (`meeting_id`);
