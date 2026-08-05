-- =====================================================================
-- V5.2 : caption_chunk — 참석자 실시간 자막 (CAP-11 수신 · CAP-12·13 조회)
-- ---------------------------------------------------------------------
-- 정본(transcript_chunk)과 성격이 다르므로 별도 테이블이다.
--   정본 = 녹음 원본을 STT로 받아쓴 것 (사후, 정확)
--   자막 = 참석자 브라우저가 회의 중에 보낸 것 (실시간, 부정확, 화자는 확실)
-- L1 화자 귀속은 이 둘을 ±1.5초 창에서 맞춰 rms 최대인 사람을 화자로 정한다.
--
-- rms 를 NOT NULL 로 둔다. CAP-11이 422 MEETING_422_2 로 거절하는 값이고,
-- 없으면 화자 확정 자체가 성립하지 않는다 — 애플리케이션 검사만 두면 뚫린다.
-- 단위는 dBFS(음수)이고 AnalyserNode 로 계산한 값이다. 모델이 아니라 산수다.
--
-- member_id 는 토큰에서 꺼낸 값만 들어온다. 본문으로 받으면 남의 이름으로
-- 발화를 심을 수 있다(CAP-11 주석 참조).
-- =====================================================================

CREATE TABLE `caption_chunk` (
    `id`              BIGINT        NOT NULL AUTO_INCREMENT,
    `meeting_id`      BIGINT        NOT NULL,
    `member_id`       BIGINT        NOT NULL COMMENT '발화자. 토큰에서 꺼낸다',
    `seq`             INT           NOT NULL COMMENT '참석자별 순번. 재전송 멱등 키',
    `start_offset_ms` INT           NOT NULL COMMENT 'startedAtEpochMs 기준 경과 ms',
    `end_offset_ms`   INT           NOT NULL,
    `text`            VARCHAR(1000) NOT NULL,
    `rms`             DECIMAL(6, 2) NOT NULL COMMENT '구간 마이크 음량(dBFS, 음수). 화자 판정의 유일한 근거',
    `created_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_CAPTION_CHUNK_MEETING_MEMBER_SEQ` (`meeting_id`, `member_id`, `seq`),
    KEY `IX_CAPTION_CHUNK_MEETING_OFFSET` (`meeting_id`, `start_offset_ms`),

    -- 값의 범위까지 막는다. NOT NULL 만으로는 뒤집힌 구간과 양수 rms 를 저장할 수 있고,
    -- 둘 다 조용히 화자 판정을 오염시킨다 — 판정이 실패하는 게 아니라 근거가 틀린 상태가 된다.
    -- CHECK 는 MySQL 8.0.16+ 부터 실제로 강제된다(로컬 8.4 · CI mysql:8.0.40).
    --
    -- 같음(=)을 허용하는 이유: 실제 오염은 뒤집힌 구간이고, 길이 0ms 자막은 무해하다.
    -- 아주 짧은 발화에서 STT 가 같은 값을 줄 수 있어 정상 입력을 거절하지 않는다.
    CONSTRAINT `CK_CAPTION_CHUNK_TIME_RANGE` CHECK (`end_offset_ms` >= `start_offset_ms`),
    CONSTRAINT `CK_CAPTION_CHUNK_OFFSET_SIGN` CHECK (`start_offset_ms` >= 0),
    -- rms 는 dBFS 라 0 이 최대(풀스케일)다. AnalyserNode 값이 [-1,1] 이므로 양수가 나올 수 없다 —
    -- 양수가 들어왔다면 단위를 잘못 계산한 것이고, 그 값으로 1·2등을 비교하면 화자가 뒤바뀐다.
    CONSTRAINT `CK_CAPTION_CHUNK_RMS_DBFS` CHECK (`rms` <= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
