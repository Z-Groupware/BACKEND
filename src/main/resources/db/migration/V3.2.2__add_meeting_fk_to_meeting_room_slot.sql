-- =====================================================================
-- V3.2.2 : meeting_room_slot → meeting FK (모성진 레인 V3.2.x)
-- ---------------------------------------------------------------------
-- 회의가 삭제되면 그 회의가 점유한 슬롯도 함께 사라져야 한다. 슬롯이 남으면 이미 없는 회의가
-- 회의실을 영구히 점유해 아무도 그 시간을 예약할 수 없다. ON DELETE CASCADE로 DB가 정리한다.
--
-- 참조 대상 meeting은 V1(baseline)에 있으므로 out-of-order 환경에서도 선행 조건이 깨지지 않는다
-- (DB_MIGRATION_RULES.md 5절 — 낮은 버전이 높은 버전의 변경에 의존하지 않는다).
-- 변경 대상 테이블은 meeting_room_slot이며 meeting 자체의 스키마는 건드리지 않는다.
-- =====================================================================

ALTER TABLE meeting_room_slot
    ADD CONSTRAINT fk_meeting_room_slot_meeting
        FOREIGN KEY (meeting_id)
            REFERENCES meeting (id)
            ON DELETE CASCADE;
