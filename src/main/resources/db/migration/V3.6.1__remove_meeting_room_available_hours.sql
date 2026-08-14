-- 모든 회의실을 24시간 예약 가능한 공통 정책으로 전환한다.
-- 회의실별 운영 시간은 더 이상 예약 가능 여부에 사용되지 않으므로 스키마에서 제거한다.
ALTER TABLE meeting_room
    DROP COLUMN available_from,
    DROP COLUMN available_to;
