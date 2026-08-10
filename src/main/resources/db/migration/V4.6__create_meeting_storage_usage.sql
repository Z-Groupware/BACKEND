-- =====================================================================
-- V4.6 : meeting_storage_usage — 회의별 현재 저장 용량 스냅샷 (김현지 레인 V4.x)
-- ---------------------------------------------------------------------
-- TokenUsageRecord(metering, V7.9)처럼 이벤트마다 행이 느는 append-only 로그가 아니다 — 회의당
-- 딱 1행이고, cap이 값이 바뀔 때마다(청크 완료·조립 완료·삭제) 그 시점의 총 사용량으로 덮어쓴다.
-- meeting_id 자체가 PK라 같은 값을 다시 report해도 멱등하다.
--
-- 회사 전체 사용량 = 이 스냅샷들의 SUM(company_id 조건) — 회의 수만큼만 행이 있어서, 회의당
-- 청크가 아무리 쌓여도(수백~수천 개) 조회 비용이 늘지 않는다.
-- =====================================================================

CREATE TABLE `meeting_storage_usage` (
    `meeting_id`   BIGINT       NOT NULL COMMENT '회의 ID(PK, 회의당 1행)',
    `company_id`   BIGINT       NOT NULL COMMENT '회사 스코프 SUM 조회용',
    `used_bytes`   BIGINT       NOT NULL COMMENT '이 회의가 현재 차지하는 총 바이트(스냅샷)',
    `updated_at`   DATETIME(6)  NOT NULL COMMENT '마지막 report 시각',
    PRIMARY KEY (`meeting_id`),
    INDEX `idx_meeting_storage_usage_company` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
