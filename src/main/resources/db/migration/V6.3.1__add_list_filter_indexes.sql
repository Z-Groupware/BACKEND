-- 목록 조회 필터/정렬 도입(2026-08-10, 이홍근 요청)에 앞서 인덱스부터 깐다.
-- 지금까지 status·assignee_member_id·due_date·created_at 전부 인덱스가 없었다.
ALTER TABLE `action`
    ADD INDEX `idx_action_assignee_status` (`assignee_member_id`, `status`),
    ADD INDEX `idx_action_team_status` (`team_id`, `status`),
    ADD INDEX `idx_action_due_date` (`due_date`),
    ADD INDEX `idx_action_created_at` (`created_at`);

ALTER TABLE `project`
    ADD INDEX `idx_project_company_status` (`company_id`, `status`),
    ADD INDEX `idx_project_created_at` (`created_at`);
