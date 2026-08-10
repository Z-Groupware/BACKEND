-- =====================================================================
-- V3.4.1 : 공지 소프트 삭제와 회사별 최신순 조회 지원
-- ---------------------------------------------------------------------
-- notice 테이블은 V1 baseline에 이미 존재하므로 다시 생성하지 않는다.
-- 삭제 이력을 보존할 컬럼과 NOTI-01 목록 조회 조건에 맞는 인덱스만 증분 추가한다.
-- =====================================================================

ALTER TABLE `notice`
    ADD COLUMN `deleted_at` DATETIME NULL COMMENT '공지 소프트 삭제 시각' AFTER `created_by`,
    MODIFY COLUMN `updated_at` DATETIME NULL DEFAULT NULL COMMENT '공지 수정 전에는 NULL';

CREATE INDEX `IDX_NOTICE_COMPANY_DELETED_CREATED`
    ON `notice` (`company_id`, `deleted_at`, `created_at` DESC, `id` DESC);
