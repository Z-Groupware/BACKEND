-- MEET-02 회의 목록의 필수 회사·기간 조건과 시작 시각 내림차순 페이징을 지원한다.
-- 동일한 start_at을 가진 회의도 id를 보조 정렬 키로 사용해 페이지 순서가 흔들리지 않게 한다.
CREATE INDEX `IX_MEETING_COMPANY_START`
    ON `meeting` (`company_id`, `start_at`, `id`);

-- 일반 구성원의 회의 목록에서 member_id로 참석 회의를 찾는 권한 EXISTS 조건을 지원한다.
-- 기존 PK(meeting_id, member_id)와 선두 컬럼이 달라 참석자 기준 조회에 별도 인덱스가 필요하다.
CREATE INDEX `IX_MEETING_ATTENDEE_MEMBER`
    ON `meeting_attendee` (`member_id`, `meeting_id`);
