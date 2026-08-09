-- =====================================================================
-- V5.1 : recording_part — 15초 녹음 청크 원장 (이태연 레인 V5.x의 create baseline)
-- ---------------------------------------------------------------------
-- CAP-04(presign 발급) → CAP-07(업로드 완료 통보) → CAP-05(조립)의 저장소.
-- 조립은 ORDER BY segment_seq, seq 로 정렬해 수행하므로 두 컬럼이 시간축의 근거다.
--
-- segment_seq : MediaRecorder 세션 단위. 탭 크래시 복구·녹음자 이어받기(takeover)마다 +1.
--               한 회의의 오디오는 여러 세그먼트로 쪼개져 들어올 수 있다.
-- UNIQUE(meeting_id, segment_seq, seq) : CAP-07 재호출 멱등성의 실체.
--               중복 기록되면 조립 시 같은 구간이 두 번 붙어 시간축이 밀린다.
--
-- meeting / member / capture_session 으로의 FK는 걸지 않는다.
--   - meeting·member : V1 baseline 소유. 암시적 FK는 각 담당자 레인에서 추가한다(V1 주석 참조)
--   - capture_session : 모성진(D) 레인에 아직 없는 테이블이다. 값만 보관한다
-- =====================================================================

CREATE TABLE `recording_part` (
    `id`                 BIGINT        NOT NULL AUTO_INCREMENT,
    `meeting_id`         BIGINT        NOT NULL,
    `capture_session_id` BIGINT        NULL COMMENT 'CAP-01이 준 캡처 세션 id (D 소유 테이블이라 FK 없음)',
    `segment_seq`        INT           NOT NULL DEFAULT 0 COMMENT 'MediaRecorder 세션 번호. 복구·녹음자 교체 시 +1',
    `seq`                INT           NOT NULL COMMENT '세그먼트 내 청크 순번 (1부터)',
    `uploader_member_id` BIGINT        NOT NULL COMMENT '이 조각을 올린 녹음자. 이어받기 추적용',
    `s3_key`             VARCHAR(1024) NULL COMMENT 'presign 발급 시 확정. 업로드 통보로 채워진다',
    `content_type`       VARCHAR(100)  NOT NULL COMMENT 'audio/webm | audio/mp4 (Safari는 webm 불가)',
    `size_bytes`         BIGINT        NOT NULL DEFAULT 0 COMMENT '용량 집계는 조립본만 센다. 조각은 청구 제외',
    `start_offset_ms`    INT           NULL COMMENT 'startedAtEpochMs 기준 경과 ms. 브라우저 시계 아님',
    `duration_ms`        INT           NULL COMMENT 'MediaRecorder 조각은 duration을 안 주므로 조립 시 복구',
    `status`             ENUM('PRESIGNED', 'UPLOADED', 'ASSEMBLED', 'MISSING') NOT NULL DEFAULT 'PRESIGNED',
    `uploaded_at`        DATETIME      NULL COMMENT 'CAP-07 통보 시각. 30초 이상 공백이면 녹음자 끊김 판정(하트비트)',
    `created_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_RECORDING_PART_MEETING_SEGMENT_SEQ` (`meeting_id`, `segment_seq`, `seq`),
    KEY `IX_RECORDING_PART_MEETING_STATUS` (`meeting_id`, `status`),
    KEY `IX_RECORDING_PART_UPLOADED_AT` (`meeting_id`, `uploaded_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
