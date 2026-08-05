-- =====================================================================
-- V5.10 : meeting_tuple_vector — few-shot 예시 원본 (AI-08 저장 · AI-09 조회)
-- ---------------------------------------------------------------------
-- MySQL이 원본이고 Qdrant는 인덱스다. RDS 트랜잭션을 먼저 커밋한 뒤 Qdrant에
-- 넣는다. 실패하면 vector_synced=false 로 남기고 재시도 워커가 처리한다 —
-- 라벨은 이미 안전하다. 순서를 뒤집으면 벡터는 있는데 원본이 없는 상태가 생긴다.
--
-- input_text 가 임베딩 대상이다. 확정 tuple 이 아니라 근거 발화 텍스트다.
-- 검색 시점에 손에 있는 건 tuple 이 아니라 새 발화이므로, tuple 을 임베딩하면
-- 쿼리와 키가 다른 공간에 놓여 유사도가 망가진다.
--   저장  근거 발화 → 벡터(key) + payload = 확정 tuple
--   검색  새 발화   → 벡터(query) → 가장 가까운 key → payload를 few-shot으로
--
-- 세 필터(company_id · layer · provenance)에 복합 인덱스를 건다. AI-09가 이 셋을
-- 필수로 요구하는 이유는 각각 다르다.
--   company_id  빠지면 다른 회사 회의 발화가 프롬프트에 주입된다 — 유출이다
--   layer       빠지면 L1.5 예시가 L4에 섞여 정확도가 떨어진다
--   provenance  빠지면 모델이 자기 출력을 예시로 다시 학습하는 루프가 생긴다
-- =====================================================================

CREATE TABLE `meeting_tuple_vector` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `company_id`      BIGINT      NOT NULL COMMENT '빠지면 정확도 문제가 아니라 타사 데이터 유출이다',
    `meeting_id`      BIGINT      NOT NULL,
    `layer`           VARCHAR(8)  NOT NULL COMMENT '이 예시가 쓰일 계층 (L1.5 · L4)',
    `dept_id`         BIGINT      NULL COMMENT 'team_id. few-shot 범위를 같은 팀으로 좁힐 때 쓴다',
    `input_text`      TEXT        NOT NULL COMMENT '임베딩 대상 = 근거 발화 텍스트 (tuple 아님)',
    `payload`         JSON        NOT NULL COMMENT '확정 tuple. 검색 결과로 그대로 실려 나간다',
    `provenance`      ENUM('HUMAN_VERIFIED', 'AUTO') NOT NULL DEFAULT 'HUMAN_VERIFIED' COMMENT 'AUTO를 예시로 쓰면 자기 출력 학습 루프가 생긴다',
    `review_log_id`   BIGINT      NULL COMMENT '이 예시를 만든 라벨. 추적용',
    `vector_synced`   BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'Qdrant 반영 여부. false면 재시도 워커 대상',
    `sync_attempts`   INT         NOT NULL DEFAULT 0,
    `synced_at`       DATETIME    NULL,
    `qdrant_point_id` VARCHAR(64) NULL COMMENT 'Qdrant 포인트 id. 재upsert·삭제에 필요',
    `created_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `IX_TUPLE_VECTOR_COMPANY_LAYER_PROVENANCE` (`company_id`, `layer`, `provenance`),
    KEY `IX_TUPLE_VECTOR_SYNC_RETRY` (`vector_synced`, `sync_attempts`),
    KEY `IX_TUPLE_VECTOR_MEETING` (`meeting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
