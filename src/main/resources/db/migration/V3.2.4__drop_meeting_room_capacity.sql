-- 회의 참석자 명단은 meeting_attendee에서 관리하므로 회의실 최대 정원 컬럼을 제거한다.
-- 이미 적용된 V1 마이그레이션은 변경하지 않고 후속 버전으로 스키마를 전진시킨다.
ALTER TABLE `meeting_room`
    DROP COLUMN `capacity`;
