-- MEET-18 비대면(온라인) 회의는 회의실 예약과 시작·종료 일시가 없다.
-- 물리 회의 전용이던 세 컬럼을 nullable로 완화하고 온라인 여부를 구분하는 컬럼을 추가한다.
ALTER TABLE `meeting`
    MODIFY COLUMN `meeting_room_id` BIGINT NULL,
    MODIFY COLUMN `start_at` DATETIME NULL,
    MODIFY COLUMN `end_at` DATETIME NULL,
    ADD COLUMN `is_online` BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'TRUE면 비대면 회의(MEET-18), 회의실·시간 없음' AFTER `end_at`;
