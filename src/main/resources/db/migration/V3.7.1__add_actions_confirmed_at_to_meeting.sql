-- 액션 분배가 확정된 비대면 회의만 일반 회의 목록에 노출하기 위한 최초 확정 시각이다.
ALTER TABLE meeting
    ADD COLUMN actions_confirmed_at DATETIME NULL AFTER canceled_at;

-- 회사별 비대면 회의 확정 여부 조회가 전체 meeting 테이블을 훑지 않도록 지원한다.
CREATE INDEX idx_meeting_company_online_confirmed
    ON meeting (company_id, is_online, actions_confirmed_at);
