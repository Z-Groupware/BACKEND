-- =====================================================================
-- V4.0 : CAP(캡처) 도메인 베이스 스키마 (김현지 레인 V4.x의 create baseline)
-- ---------------------------------------------------------------------
-- recording_part / capture_upload_state 두 테이블 생성. 서로 없이는 청크 업로드 완료
-- 통보(complete)가 동작하지 않는 한 세트라 한 파일에 담는다(1파일-1변경의 create 예외,
-- V7.0 handover 선례와 동일한 근거: 함께 도입되는 단일 논리 단위).
-- 이후 증분(recording 상태 컬럼, caption 테이블 등)은 V4.1.x부터 이어간다.
-- =====================================================================

-- 청크 개별 업로드 기록. UNIQUE(meeting_id, segment_seq, seq)가 멱등성의 물리적 근거
-- (parts/{seq}/complete가 재호출돼도 중복 행이 안 생기고 DataIntegrityViolationException -> 409로 매핑).
CREATE TABLE `recording_part` (
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `meeting_id`   BIGINT       NOT NULL COMMENT '회의 ID',
    `segment_seq`  INT          NOT NULL COMMENT 'MediaRecorder 세션 번호(탭 크래시 복구·녹음자 교체 시 증가)',
    `seq`          INT          NOT NULL COMMENT '청크 순번 (세그먼트 내 1부터)',
    `s3_key`       VARCHAR(512) NOT NULL COMMENT 'stt-temp/org-{orgId}/meeting-{meetingId}/parts/{seq}.webm',
    `size_bytes`   BIGINT       NOT NULL COMMENT '업로드된 청크 크기(byte), 상한 검증용',
    `uploaded_by`  BIGINT       NULL     COMMENT '업로드 시점 recorder member id',
    `created_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_RECORDING_PART_MEETING_SEGMENT_SEQ` UNIQUE (`meeting_id`, `segment_seq`, `seq`),
    CONSTRAINT `FK_recording_part_meeting` FOREIGN KEY (`meeting_id`) REFERENCES `meeting` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 회의당 1행 — CAP 자체 세그먼트/현재 녹음자 북키핑. D(회의) 도메인의 미래 capture_session과는
-- 별개 개념: 저 테이블은 아직 없고(D 미구현), "현재 녹음자가 누구인가"는 CAP이 직접 소유해야
-- presign/complete가 D를 기다리지 않고 지금 바로 동작한다.
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
