-- =====================================================================
-- V3.5.1 : notification 테이블 type ENUM에 알림 종류 4건 추가
-- ---------------------------------------------------------------------
-- 기존 3종(MEETING_CREATED/MEETING_REMINDER/MEETING_CANCELED) 옆에 두 묶음을 더한다.
--
-- 1. MEETING_ATTENDEE_ADDED / MEETING_ATTENDEE_REMOVED — MEET-09(참석자 명단 교체)가
--    새로 추가되거나 제외된 구성원에게 보내는 알림이다. D(회의) 도메인 소유.
-- 2. ANALYSIS_COMPLETED / ANALYSIS_FAILED — 회의 종료 후 A(분석) 도메인의 백그라운드
--    요약이 끝나거나 실패했을 때 회의 개설자에게 보내는 알림이다.
--
-- 둘 다 저장형으로 둔다 — NOTICE_CREATED처럼 저장 없이 SSE로만 보내면
-- V4.3.1이 추가한 UNIQUE(company_id, recipient_member_id, type, meeting_id) 중복 방지를
-- 못 쓰고, 알림 발송 시점에 연결이 없던 사용자는 그 사실을 알림 목록에서 영영 볼 수 없다.
-- 특히 분석 쪽은 조립·STT·분석 사이 자동 재트리거가 필요한 구조라 같은 완료·실패 신호가
-- 두 번 발행될 수 있어 DB 중복 방지가 필요하다.
--
-- notification 테이블 자체는 김현지 레인(V4.x)에서 만들고 확장해 왔으나, 마이그레이션
-- 레인 번호는 파일 버전 충돌을 피하기 위한 개인별 네임스페이스일 뿐 테이블 소유를
-- 뜻하지 않아 이번 확장은 D(V3.x)에서 진행한다.
-- =====================================================================

ALTER TABLE `notification`
    MODIFY COLUMN `type` ENUM(
        'MEETING_CREATED',
        'MEETING_REMINDER',
        'MEETING_CANCELED',
        'MEETING_ATTENDEE_ADDED',
        'MEETING_ATTENDEE_REMOVED',
        'ANALYSIS_COMPLETED',
        'ANALYSIS_FAILED'
    ) NOT NULL;
