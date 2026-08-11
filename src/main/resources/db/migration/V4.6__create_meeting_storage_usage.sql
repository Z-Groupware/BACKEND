-- =====================================================================
-- V4.6 : meeting_storage_usage — 회의별 현재 저장 용량 스냅샷 (김현지 레인 V4.x)
-- ---------------------------------------------------------------------
-- ⚠️ 배포 순서 주의: V4.5와 동일 — 운영 out-of-order 비활성 상태에서, V7.x가 먼저 배포되면
-- 이 마이그레이션이 막힌다. 이 시점(2026-08-11) 운영 최신 버전은 3.1.2라 지금은 안전하다.
-- 배포 전에 운영 flyway_schema_history를 다시 확인할 것.
--
-- TokenUsageRecord(metering, V7.9)처럼 이벤트마다 행이 느는 append-only 로그가 아니다 — 회의당
-- 딱 1행이고, cap이 값이 바뀔 때마다(청크 완료·조립 완료·삭제) 그 시점의 총 사용량으로 덮어쓴다.
-- meeting_id 자체가 PK라 같은 값을 다시 report해도 멱등하다.
--
-- 회사 전체 사용량 = 이 스냅샷들의 SUM(company_id 조건) — 회의 수만큼만 행이 있어서, 회의당
-- 청크가 아무리 쌓여도(수백~수천 개) 조회 비용이 늘지 않는다.
--
-- revision(호출자 단조 증가 순번)을 둔다 — 서버 수신 시각만으로 순서를 판단하면, report가
-- 네트워크 지연으로 뒤바뀐 순서로 도착했을 때 최신 값을 오래된 값이 덮어써 사용량이 과소
-- 집계되고 cap의 한도 판정을 우회할 수 있다(CodeRabbit 지적) — revision이 기존보다 클 때만
-- 반영한다(MeetingStorageUsagePersistenceAdapter.reportIfNewer).
-- =====================================================================

CREATE TABLE `meeting_storage_usage` (
    `meeting_id`   BIGINT       NOT NULL COMMENT '회의 ID(PK, 회의당 1행)',
    `company_id`   BIGINT       NOT NULL COMMENT '회사 스코프 SUM 조회용',
    `used_bytes`   BIGINT       NOT NULL COMMENT '이 회의가 현재 차지하는 총 바이트(스냅샷)',
    `revision`     BIGINT       NOT NULL COMMENT '호출자 단조 증가 순번 — 이보다 작거나 같은 report는 무시',
    `updated_at`   DATETIME(6)  NOT NULL COMMENT '마지막 반영된 report 시각',
    PRIMARY KEY (`meeting_id`),
    INDEX `idx_meeting_storage_usage_company` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
