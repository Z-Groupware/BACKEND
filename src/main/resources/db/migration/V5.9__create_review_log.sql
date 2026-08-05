-- =====================================================================
-- V5.9 : review_log — 사람 라벨 로그 (RVW-02 · ANLZ-04가 남긴다)
-- ---------------------------------------------------------------------
-- 특화 모델의 유일한 연료다. 지금 넣지 않으면 나중에 붙여도 그때까지의
-- 데이터가 없고, 지나간 회의는 다시 만들 수 없다.
--
-- CONFIRM(무수정 승인)도 반드시 기록한다. 수정·반려만 남기면 라벨셋에 오답
-- 사례만 쌓여 분포가 왜곡된다 — AI가 맞힌 것도 똑같이 정답 라벨이다.
--
-- reject_reason 이 곧 어느 계층이 틀렸는지를 가리킨다. 이게 없으면 "틀렸다"만
-- 남아서 정확도가 나쁠 때 어디를 고쳐야 할지 조사를 처음부터 다시 해야 한다.
--   WRONG_ASSIGNEE → L1.5 · WRONG_DUE → L4 · HALLUCINATION → L3·L4
--   NOT_CONFIRMED  → L3.5 · DUPLICATE → L2 경계
--
-- input_context 는 넉넉히 담는다 — usedFewShot · 근거 발화 · roster 까지.
-- 나중에 재현할 수 없는 유일한 값이다.
--
-- is_manual : RVW-03으로 사람이 직접 추가한 액션. AI 입력이 없어 {입력→정답}
-- 쌍이 성립하지 않으므로 few-shot 풀에서 제외한다. gold set 라벨로는 유효하다.
--
-- 갱신하지 않는 append-only 로그라 updated_at 을 두지 않는다.
-- =====================================================================

CREATE TABLE `review_log` (
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `company_id`     BIGINT      NOT NULL COMMENT '테넌트 스코프. few-shot 조회 필터의 첫 조건',
    `meeting_id`     BIGINT      NOT NULL,
    `target_type`    ENUM('ACTION', 'SUMMARY_ITEM') NOT NULL COMMENT 'ACTION=action.id / SUMMARY_ITEM=meeting_decision.id',
    `target_id`      BIGINT      NOT NULL,
    `layer`          VARCHAR(8)  NOT NULL COMMENT '이 라벨이 교정하는 계층 (L1.5 · L3 · L3.5 · L4)',
    `decision`       ENUM('CONFIRM', 'MODIFY', 'REJECT') NOT NULL COMMENT 'CONFIRM도 반드시 남긴다',
    `reject_reason`  ENUM('WRONG_ASSIGNEE', 'WRONG_DUE', 'HALLUCINATION', 'NOT_CONFIRMED', 'DUPLICATE') NULL COMMENT 'MODIFY·REJECT에는 필수(422 MEETING_422_3)',
    `input_context`  JSON        NOT NULL COMMENT 'usedFewShot · 근거 발화 · roster. 재현 불가한 값이라 넉넉히 담는다',
    `llm_output`     JSON        NOT NULL COMMENT 'AI가 낸 원본 값',
    `human_value`    JSON        NULL COMMENT '사람이 고친 값. CONFIRM이면 NULL(=llm_output과 동일)',
    `confirmed_by`   BIGINT      NOT NULL COMMENT '확정한 member_id',
    `model_name`     VARCHAR(60) NULL,
    `prompt_version` VARCHAR(20) NULL,
    `is_manual`      BOOLEAN     NOT NULL DEFAULT FALSE COMMENT 'RVW-03 직접 추가 건. few-shot 풀 제외, gold set은 유효',
    `created_at`     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `IX_REVIEW_LOG_MEETING` (`meeting_id`),
    KEY `IX_REVIEW_LOG_COMPANY_LAYER_DECISION` (`company_id`, `layer`, `decision`),
    KEY `IX_REVIEW_LOG_TARGET` (`target_type`, `target_id`),

    -- decision 과 reject_reason 의 조합을 DB 가 강제한다. 애플리케이션 검사(422 MEETING_422_3)만
    -- 두면 라벨셋에 "사유 없는 반려"가 섞이고, 그 행은 어느 계층을 고쳐야 할지 가리키지 못해
    -- 라벨로서 쓸모가 없어진다. 반대로 CONFIRM 에 사유가 붙으면 "맞혔는데 틀렸다"는 모순이 된다.
    --
    -- MODIFY 를 target_type 별로 나누는 이유: 액션 수정은 바뀐 필드로 사유를 자동 추론할 수 있지만
    -- (WRONG_ASSIGNEE · WRONG_DUE), 요약 항목은 문구만 다듬는 수정이 있어 대응하는 사유 코드가 없다.
    -- 요약까지 사유를 강제하면 ANLZ-04 수정 기록이 아예 남지 못한다.
    CONSTRAINT `CK_REVIEW_LOG_REASON` CHECK (
        (`decision` = 'CONFIRM' AND `reject_reason` IS NULL)
        OR (`decision` = 'REJECT' AND `reject_reason` IS NOT NULL)
        OR (`decision` = 'MODIFY'
            AND (`target_type` = 'SUMMARY_ITEM' OR `reject_reason` IS NOT NULL))
    )
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
