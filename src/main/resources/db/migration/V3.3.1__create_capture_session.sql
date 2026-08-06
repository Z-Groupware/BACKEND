-- =====================================================================
-- V3.3.1 : D 도메인 캡처 세션 원본 테이블 생성 (모성진 레인 V3.3.x)
-- ---------------------------------------------------------------------
-- CAP-01~03과 CAP-10은 회의당 하나인 캡처 세션의 생명주기를 이 테이블에서 관리한다.
-- D는 세션 식별자·상태·시간축 기준점만 소유하며, 현재 녹음자·이어받기·청크·STT는
-- A 도메인의 capture_upload_state와 후속 파이프라인이 소유한다.
--
-- 서비스의 exists 조회는 친절한 조기 검증일 뿐이다. 같은 회의에 동시에 들어온 두 요청이
-- 모두 "세션 없음"을 읽을 수 있으므로 UNIQUE(meeting_id)를 최종 동시성 관문으로 둔다.
-- 해당 제약 위반은 CaptureSessionPersistenceAdapter에서 CS-002로 변환한다.
--
-- started_by는 세션을 연 회의 개설자이며 현재 녹음자가 아니다. 녹음자 원본을 D에도 두면
-- A의 presign·heartbeat·takeover 흐름과 값이 갈라지므로 recorder_member_id는 만들지 않는다.
-- started_at은 KST 로컬 일시 원본이고, started_at_epoch_ms는 브라우저 자막·청크가 공유할
-- Unix epoch 밀리초 기준점이다.
-- =====================================================================

CREATE TABLE capture_session (
    id                  BIGINT      NOT NULL AUTO_INCREMENT,
    meeting_id          BIGINT      NOT NULL,
    started_by          BIGINT      NOT NULL COMMENT '세션을 연 host. 현재 녹음자는 A 도메인이 소유한다',
    status              ENUM('ACTIVE', 'PAUSED', 'ENDED') NOT NULL DEFAULT 'ACTIVE',
    started_at          DATETIME    NOT NULL COMMENT '캡처 세션 시작 KST 일시. meeting.started_at과 별개다',
    started_at_epoch_ms BIGINT      NOT NULL COMMENT '자막·청크 오프셋의 서버 기준 Unix epoch 밀리초',
    paused_at           DATETIME    NULL,
    ended_at            DATETIME    NULL,
    created_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT UK_CAPTURE_SESSION_MEETING UNIQUE (meeting_id)
) ENGINE=InnoDB
  DEFAULT CHARSET=utf8mb4
  COLLATE=utf8mb4_unicode_ci;
