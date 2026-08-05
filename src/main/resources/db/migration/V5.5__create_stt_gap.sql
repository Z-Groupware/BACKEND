-- =====================================================================
-- V5.5 : stt_gap — 받아쓰기 구멍 (CAP-06 응답의 gaps)
-- ---------------------------------------------------------------------
-- 구멍을 숨기면 담당자가 누락을 모른 채 분배하고, 그 액션은 영구히 사라진다.
-- 그래서 구멍은 상태가 아니라 레코드로 남긴다.
--
-- mentioned_names · keywords 를 함께 저장하는 이유 : 구간만 알려주면 담당자가
-- 10분을 다시 들어야 한다. "20:00~30:00, 김민섭 언급, 광고·섭외 키워드"까지
-- 좁혀주면 30초만 확인한다. 자막(caption_chunk)이 살아 있으면 STT가 죽은
-- 구간에서도 이 두 값은 뽑을 수 있다.
--
-- resolved_at : 사람이 확인한 시각. RVW-05(분배 확정)와 CAP-15(녹음 삭제)가
-- 409로 막는 조건이 이 컬럼이다 — NULL 이 남아 있으면 ?confirm=true 로만 강행된다.
-- =====================================================================

CREATE TABLE `stt_gap` (
    `id`                 BIGINT   NOT NULL AUTO_INCREMENT,
    `meeting_id`         BIGINT   NOT NULL,
    `start_offset_ms`    INT      NOT NULL,
    `end_offset_ms`      INT      NOT NULL,
    `reason`             ENUM('STT_FAILED', 'UPLOAD_MISSING', 'NO_AUDIO', 'ASSEMBLY_GAP') NOT NULL COMMENT 'UPLOAD_MISSING=청크 유실 / ASSEMBLY_GAP=녹음 자체가 없던 구간(무음으로 채움)',
    `stt_block_seq`      INT      NULL COMMENT '원인 블록. UPLOAD_MISSING 이면 NULL',
    `mentioned_names`    JSON     NULL COMMENT '이 구간 자막에서 뽑은 언급 인물. 확인 범위를 좁힌다',
    `keywords`           JSON     NULL COMMENT '이 구간 자막 키워드',
    `resolved_at`        DATETIME NULL COMMENT '사람이 다시 듣기로 확인한 시각. NULL이면 RVW-05가 409로 막는다',
    `resolved_by`        BIGINT   NULL COMMENT '확인한 member_id',
    `created_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `IX_STT_GAP_MEETING_RESOLVED` (`meeting_id`, `resolved_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
