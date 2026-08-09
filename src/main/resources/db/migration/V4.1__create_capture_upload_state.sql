-- =====================================================================
-- V4.1 : capture_upload_state — 회의 단위 캡처 업로드 북키핑 (김현지 레인 V4.x)
-- ---------------------------------------------------------------------
-- recording_part(이태연 V5.1)가 "청크 하나하나"의 원장이라면, 이 테이블은 그 위의
-- "회의 단위 집계·소유권" 상태다. 청크 행에서 관측(MAX(seq) 등)만 되는 것이 아니라
-- 결정 상태로 담아야 하는 것들:
--   - recorder_person_id : 지금 이 회의의 녹음자가 누구인지(첫 presign 호출자 → 이후 takeover로 교체).
--                          "관측"이 아니라 "배정 결정"이라 별도 행으로 소유한다.
--   - segment_seq        : 현재 MediaRecorder 세션 번호(이어받기마다 +1).
--   - last_seq           : 현재 세그먼트에서 마지막으로 완료 통보된 청크 순번.
--   - blocks_formed      : 지금까지 형성된 STT 블록 수(이태연 stt_block과 연동될 카운터).
--
-- 회의당 1행(meeting_id PK). meeting FK는 이 레인(V4.x)에서 명시적으로 건다
-- (recording_part는 값만 보관하지만, 이 집계 테이블은 회의 생명주기에 종속되므로 FK로 묶는다).
-- =====================================================================

CREATE TABLE `capture_upload_state` (
    `meeting_id`          BIGINT   NOT NULL COMMENT '회의 ID (PK, 회의당 1행)',
    `segment_seq`         INT      NOT NULL DEFAULT 0 COMMENT '현재 세그먼트 번호',
    `recorder_person_id`  BIGINT   NULL     COMMENT '현재 녹음자 member id. 최초 presign 호출자로 설정',
    `last_seq`            INT      NOT NULL DEFAULT 0 COMMENT '마지막으로 완료 통보된 청크 순번(세그먼트 내)',
    `blocks_formed`       INT      NOT NULL DEFAULT 0 COMMENT '지금까지 형성된 STT 블록 개수',
    `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`meeting_id`),
    CONSTRAINT `FK_capture_upload_state_meeting` FOREIGN KEY (`meeting_id`) REFERENCES `meeting` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
