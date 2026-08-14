-- =====================================================================
-- V4.8 : meeting_storage_usage에 project_id 반정규화 추가 (김현지 레인 V4.x)
-- ---------------------------------------------------------------------
-- ⚠️ 배포 순서 주의: V4.5·V4.6과 동일 — 운영 out-of-order 비활성 상태에서, 다른 레인(V5.x 이후)이
-- 먼저 배포되면 이 마이그레이션이 막힌다. 배포 전에 운영 flyway_schema_history를 다시 확인할 것.
--
-- 저장소 관리 화면(/manage/storage)에서 프로젝트별 사용량을 집계해야 하는데, 지금
-- meeting_storage_usage는 meeting_id/company_id만 있어 매번 meeting 테이블과 조인해야
-- 한다. Meeting.projectId가 불변(생성 후 절대 안 바뀜)이라 리포트 시점에 함께 저장해도
-- stale해질 일이 없다 — 그래서 조인 대신 반정규화한다.
--
-- 기존 행은 이 컬럼이 없어 NULL로 추가한 뒤, meeting 테이블에서 즉시 백필하고 나서
-- NOT NULL로 잠근다(meeting.project_id 자체가 V1부터 NOT NULL이라 백필 후에는 항상 채워진다).
-- =====================================================================

ALTER TABLE `meeting_storage_usage`
    ADD COLUMN `project_id` BIGINT NULL COMMENT '리포트 시점 meeting.project_id 반정규화 — 프로젝트별 집계용' AFTER `company_id`;

UPDATE `meeting_storage_usage` u
    JOIN `meeting` m ON m.id = u.meeting_id
    SET u.project_id = m.project_id
    WHERE u.project_id IS NULL;

ALTER TABLE `meeting_storage_usage`
    MODIFY COLUMN `project_id` BIGINT NOT NULL COMMENT '리포트 시점 meeting.project_id 반정규화 — 프로젝트별 집계용';

CREATE INDEX `idx_meeting_storage_usage_company_project` ON `meeting_storage_usage` (`company_id`, `project_id`);
