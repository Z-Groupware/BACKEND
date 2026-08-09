-- =====================================================================
-- V5.8 : meeting_decision — 주제별 결정 · 논의 · 블로커 (L3 산출 · L3.5 게이트)
-- ---------------------------------------------------------------------
-- ⚠ V5.7과 같은 소유권 미결 상태다. 머지 전 확정할 것.
--
-- reason 을 컬럼으로 둔다. L3가 왜 이 항목을 결정으로 분류했는지 남기지 않으면,
-- 나중에 오분류를 발견해도 프롬프트의 어느 부분이 문제였는지 되짚을 수 없다.
--
-- evidence_transcript_id 는 정확도 4원칙의 '근거 강제'를 스키마로 받는 자리다.
-- 근거 발화가 없는 항목은 애초에 반환하지 않으므로 실제로는 항상 채워진다.
-- NULL 을 허용하는 건 자막 폴백 경로(STT 전멸 시 caption_chunk 로 분석)와
-- 사람이 직접 추가한 항목 때문이다.
--
-- gate_status = L3.5 판정. CONFIRMED 만 L4(tuple 추출)로 넘어간다.
-- DISCUSSED 를 버리지 않고 남기는 이유 : "이 안건은 담당자가 안 정해졌다"고
-- 짚어주는 것 자체가 결과물이고, 나중에 게이트를 조일지 풀지 판단할 근거다.
--
-- FK 는 같은 레인 안의 meeting_summary 에만 건다(V5.7 = 낮은 버전이므로
-- out-of-order 규칙 §5에 걸리지 않는다).
-- =====================================================================

CREATE TABLE `meeting_decision` (
    `id`                     BIGINT        NOT NULL AUTO_INCREMENT,
    `meeting_summary_id`     BIGINT        NOT NULL,
    `meeting_id`             BIGINT        NOT NULL COMMENT '조회 편의를 위한 반정규화',
    `topic_seq`              INT           NOT NULL DEFAULT 0 COMMENT 'L2가 나눈 주제 순번',
    `topic`                  VARCHAR(300)  NOT NULL COMMENT '주제명. meeting_topic(D)과는 별개로 L2가 만든 것',
    `item_type`              ENUM('DECISION', 'DISCUSSION', 'BLOCKER') NOT NULL,
    `content`                TEXT          NOT NULL,
    `reason`                 VARCHAR(1000) NULL COMMENT 'L3가 이렇게 분류한 근거. 오분류 조사의 출발점',
    `evidence_transcript_id` BIGINT        NULL COMMENT 'transcript_chunk.id. 근거 없는 항목은 애초에 반환되지 않는다',
    `gate_status`            ENUM('CONFIRMED', 'DISCUSSED') NULL COMMENT 'L3.5 판정. CONFIRMED만 L4로 넘어간다',
    `sort_order`             INT           NOT NULL DEFAULT 0,
    `created_at`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`             DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `IX_MEETING_DECISION_MEETING_TYPE` (`meeting_id`, `item_type`),
    KEY `IX_MEETING_DECISION_SUMMARY` (`meeting_summary_id`),
    -- 복합 FK 로 "같은 회의"까지 강제한다. meeting_summary_id 만 참조하면 요약의 존재만 검증되고,
    -- 다른 회의의 요약을 가리키면서 meeting_id 는 딴 값인 행을 저장할 수 있다.
    -- 그러면 회의 상세 화면이 남의 회의 결정사항을 자기 것으로 보여준다 — 조회는 성공하므로
    -- 아무도 오류를 못 본다. 반정규화한 meeting_id 의 대가를 여기서 치른다.
    CONSTRAINT `FK_MEETING_DECISION_SUMMARY`
        FOREIGN KEY (`meeting_summary_id`, `meeting_id`)
        REFERENCES `meeting_summary` (`id`, `meeting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
