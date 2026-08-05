-- =====================================================================
-- V5.4 : stt_block — 10분 단위 STT 블록 상태 (STT-03 조회 · STT-04 재처리)
-- ---------------------------------------------------------------------
-- 청크가 10분치(40개) 모이면 뒤쪽 ±20초에서 700ms 이상 무음을 찾아 절단하고
-- 블록을 만든다. 회의가 끝나기를 기다리지 않고 진행 중에 곧바로 STT를 돌린다.
--
-- cut_reason 을 남기는 이유 : VAD가 무음을 못 찾아 FALLBACK_OVERLAP 으로 자른
-- 블록은 경계에서 발화가 잘렸을 수 있다. 나중에 품질을 조사할 때 이 값이 없으면
-- 왜 그 구간만 정확도가 낮은지 알 수 없다.
--
-- provider_job_name 에 retryCount 를 포함시킨다 — AWS Transcribe 잡 이름은
-- 계정 내 유일해야 해서 재시도 때 같은 이름을 쓰면 실패한다
-- (meeting-500-block-3-r3). UNIQUE 로 그 실수를 DB에서 잡는다.
-- =====================================================================

CREATE TABLE `stt_block` (
    `id`                BIGINT        NOT NULL AUTO_INCREMENT,
    `meeting_id`        BIGINT        NOT NULL,
    `block_seq`         INT           NOT NULL COMMENT '회의 내 블록 순번 (0부터)',
    `start_offset_ms`   INT           NOT NULL,
    `end_offset_ms`     INT           NOT NULL,
    `cut_reason`        ENUM('VAD_SILENCE', 'FALLBACK_OVERLAP', 'TAIL', 'WHOLE_FILE') NOT NULL COMMENT 'FALLBACK_OVERLAP=무음 못 찾음(경계 발화 잘림 가능) / WHOLE_FILE=CAP-10 수동 업로드',
    `provider`          VARCHAR(40)   NOT NULL DEFAULT 'aws-transcribe' COMMENT 'aws-transcribe | whisper',
    `provider_job_name` VARCHAR(200)  NULL COMMENT '계정 내 유일해야 함. retryCount 포함 (meeting-500-block-3-r3)',
    `audio_s3_key`      VARCHAR(1024) NULL COMMENT '블록 오디오. 두 EC2 사이 파일 전달은 S3 경유만',
    `status`            ENUM('PENDING', 'QUEUED', 'RUNNING', 'DONE', 'FAILED') NOT NULL DEFAULT 'PENDING',
    `retry_count`       INT           NOT NULL DEFAULT 0,
    `error_code`        VARCHAR(50)   NULL COMMENT 'JOB_FAILED 등. 사용자에게 그대로 노출하지 않는다',
    `started_at`        DATETIME      NULL,
    `finished_at`       DATETIME      NULL,
    `created_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_STT_BLOCK_MEETING_SEQ` (`meeting_id`, `block_seq`),
    UNIQUE KEY `UK_STT_BLOCK_PROVIDER_JOB_NAME` (`provider_job_name`),
    KEY `IX_STT_BLOCK_MEETING_STATUS` (`meeting_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
