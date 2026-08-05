ALTER TABLE `action`
    ADD COLUMN `assignee_source` ENUM('EXPLICIT_CALL', 'FIRST_PERSON') NULL
        COMMENT 'AI가 담당자를 특정한 근거 — 명시적 호명/1인칭 발화. 판정 불가 시 NULL',
    ADD COLUMN `evidence_transcript_id` BIGINT NULL
        COMMENT '이 액션의 근거가 된 회의 발화 id',
    ADD COLUMN `gate_signals` JSON NULL
        COMMENT '자동확정 4조건 판정 결과',
    ADD COLUMN `is_manual` BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '사람이 직접 추가한 액션인지 여부';
