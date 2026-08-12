-- =====================================================================
-- V7.12 : token_usage_outbox — 원장 기록의 보장된 전달(guaranteed delivery)
-- ---------------------------------------------------------------------
-- 문제: 분석 완료 시 캡처가 미터링에 토큰 사용량을 기록하는데, 그 기록이
-- 실패하면(DB 순단·데드락·타임아웃) 지금까지는 로그만 남기고 원장 한 줄이
-- 영구 유실됐다 — 복구 장치가 없었다. 과금에서 이건 과소청구다.
--
-- 해법: 원장 싱크는 이미 job_id UNIQUE 로 멱등하다(중복=no-op). 그래서
-- 재시도가 안전하다. 이 outbox 는 "실패한 기록"만 담는 durable 큐다.
--   · 정상 경로: 원장에 바로 INSERT(동기). outbox 를 거치지 않는다.
--   · 실패 경로: 여기에 PENDING 으로 적재 → 릴레이가 백오프 재시도 →
--     성공하면 DONE, 한계 시도 초과분은 DEAD(관측 가능한 dead-letter).
-- 멱등 싱크(중복 차단) + at-least-once 릴레이(재시도) = 효과적 exactly-once.
--
-- job_id UNIQUE: 같은 실행 job 이 두 번 적재되지 않는다(원장과 동일한 키).
-- (status, next_attempt_at) 인덱스: 릴레이가 "재시도할 때가 된 PENDING"만 훑는다.
-- =====================================================================

CREATE TABLE token_usage_outbox (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    job_id          VARCHAR(100) NOT NULL,
    company_id      BIGINT       NOT NULL,
    team_id         BIGINT       NULL,
    meeting_id      BIGINT       NOT NULL,
    input_tokens    INT          NOT NULL,
    output_tokens   INT          NOT NULL,
    model           VARCHAR(100) NOT NULL,
    status          ENUM('PENDING', 'DONE', 'DEAD') NOT NULL DEFAULT 'PENDING',
    attempt_count   INT          NOT NULL DEFAULT 0 COMMENT '적용 시도 횟수. 한계 초과 시 DEAD',
    last_error      VARCHAR(500) NULL COMMENT '마지막 실패 원인(잘려 저장). 조사 출발점',
    next_attempt_at DATETIME(6)  NOT NULL COMMENT '이 시각 이후에 릴레이가 재시도',
    created_at      DATETIME(6)  NOT NULL,
    updated_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_token_usage_outbox_job_id UNIQUE (job_id),
    INDEX idx_token_usage_outbox_status_due (status, next_attempt_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
