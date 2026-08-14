-- =====================================================================
-- V4.9 : meeting_text_storage_usage — 회의별 자막·요약 저장 용량 스냅샷 (김현지 레인 V4.x)
-- ---------------------------------------------------------------------
-- ⚠️ 배포 순서 주의: V4.5·V4.6·V4.8과 동일 — 운영 out-of-order 비활성 상태에서, 다른 레인
-- (V5.x 이후)이 먼저 배포되면 이 마이그레이션이 막힌다. 배포 전에 운영 flyway_schema_history를
-- 다시 확인할 것.
--
-- meeting_storage_usage(V4.6, 음성 바이트)와 같은 스냅샷 패턴이지만, 이 값은 서로 다른 세 producer
-- (cap의 caption_chunk, capture의 transcript_chunk·meeting_summary)가 **같은 회의에 각자** 리포트한다.
-- 컬럼 하나(used_bytes)로 두면 나중에 리포트하는 쪽이 앞선 producer의 값을 통째로 덮어써 총합이
-- 아니라 "마지막으로 리포트한 값"이 되어버린다 — 그래서 소스별로 바이트·revision을 따로 둔다.
-- 회사 전체 자막·요약 사용량 = SUM(caption_bytes + transcript_bytes + summary_bytes).
--
-- revision(소스별)은 호출 시점 System.currentTimeMillis()를 쓴다(음성처럼 고정 상수가 아니다) —
-- 같은 소스가 같은 회의에 여러 번 리포트하므로(STT 블록마다·재요약마다) 매번 실제로 더 큰 값이
-- 보장돼야 순서 역전을 막을 수 있다. 세 소스는 서로 다른 producer가 각자의 컬럼만 갱신하므로
-- revision을 공유하지 않는다 — 공유하면 한 소스의 리포트가 다른 소스의 컬럼까지 "최신"으로
-- 오판하게 만든다.
-- =====================================================================

CREATE TABLE `meeting_text_storage_usage` (
    `meeting_id`          BIGINT       NOT NULL COMMENT '회의 ID(PK, 회의당 1행)',
    `company_id`          BIGINT       NOT NULL COMMENT '회사 스코프 SUM 조회용',
    `project_id`          BIGINT       NOT NULL COMMENT '리포트 시점 meeting.project_id 반정규화 — 프로젝트별 집계용',
    `caption_bytes`       BIGINT       NOT NULL DEFAULT 0 COMMENT 'cap(SubmitCaptionsService)이 리포트하는 caption_chunk 총 바이트',
    `caption_revision`    BIGINT       NOT NULL DEFAULT 0 COMMENT 'caption_bytes 전용 revision(epoch millis)',
    `transcript_bytes`    BIGINT       NOT NULL DEFAULT 0 COMMENT 'capture(SttResultPollingService)가 리포트하는 transcript_chunk 총 바이트',
    `transcript_revision` BIGINT       NOT NULL DEFAULT 0 COMMENT 'transcript_bytes 전용 revision(epoch millis)',
    `summary_bytes`       BIGINT       NOT NULL DEFAULT 0 COMMENT 'capture(MeetingSummaryPersistenceAdapter)가 리포트하는 meeting_summary+meeting_decision 총 바이트',
    `summary_revision`    BIGINT       NOT NULL DEFAULT 0 COMMENT 'summary_bytes 전용 revision(epoch millis)',
    `updated_at`          DATETIME(6)  NOT NULL COMMENT '마지막으로 반영된 report(어느 소스든) 시각',
    PRIMARY KEY (`meeting_id`),
    INDEX `idx_meeting_text_storage_usage_company_project` (`company_id`, `project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
