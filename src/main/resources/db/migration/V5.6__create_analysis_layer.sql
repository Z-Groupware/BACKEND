-- =====================================================================
-- V5.6 : analysis_layer — 계층별 실행 상태 · 토큰 (ANLZ-02 재개 · QLTY-03 비용)
-- ---------------------------------------------------------------------
-- 이 테이블이 세 가지를 동시에 떠받친다.
--
-- ① 계층 재개(ANLZ-02) — 실패한 계층부터 이어서 돌린다. 이전 계층 결과가
--    DB에 있어야 재계산하지 않는다. 처음부터 다시 돌리면 그만큼 재과금된다.
-- ② 중복 호출 방지 — SQS는 at-least-once라 중복 수신은 언젠가 반드시 온다.
--    UNIQUE(meeting_id, layer) + status='RUNNING' 이 잠금 역할을 한다.
-- ③ 비용 기준선 — tokens_in/out 이 없으면 QLTY-03이 성립하지 않고, 특화 모델
--    전환의 손익분기점을 계산할 수 없다. 나중에 붙이면 그때까지 데이터가 없다.
--
-- layer 를 ENUM 이 아니라 VARCHAR 로 둔다. L1.5·L3.5 처럼 중간 계층이 늘어날
-- 여지가 있고, 계층 추가마다 공용 ALTER를 도는 것보다 낫다.
--
-- attempt_count 는 계층 단위 재시도 횟수다(백오프 3회 후 FAILED). 워커 단위
-- 백오프(AI EC2가 통째로 내려간 경우)와는 다른 축이므로 여기 두지 않는다.
-- =====================================================================

CREATE TABLE `analysis_layer` (
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `meeting_id`     BIGINT       NOT NULL,
    `layer`          VARCHAR(8)   NOT NULL COMMENT 'L1 · L1.5 · L2 · L3 · L3.5 · L4 · L5 · L6 · L7',
    `status`         ENUM('PENDING', 'RUNNING', 'DONE', 'FAILED', 'SKIPPED') NOT NULL DEFAULT 'PENDING' COMMENT 'RUNNING이 중복 실행 잠금',
    `attempt_count`  INT          NOT NULL DEFAULT 0 COMMENT '계층 단위 재시도. 3회 후 FAILED + 잡 PAUSED',
    `tokens_in`      INT          NOT NULL DEFAULT 0 COMMENT '없으면 비용 기준선이 사라진다. 선택 항목이 아니다',
    `tokens_out`     INT          NOT NULL DEFAULT 0,
    `model_name`     VARCHAR(60)  NULL COMMENT 'gemini-flash 등. 코드 계층(L1·L6·L7)은 NULL',
    `prompt_version` VARCHAR(20)  NULL COMMENT '프롬프트를 바꾼 뒤 정확도 변화를 추적하는 유일한 키',
    `error_code`     VARCHAR(50)  NULL COMMENT 'SCHEMA_INVALID · CONTEXT_EXCEEDED 는 즉시 FAILED(재시도해도 토큰만 태운다)',
    `error_message`  VARCHAR(500) NULL,
    `started_at`     DATETIME     NULL,
    `finished_at`    DATETIME     NULL,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_ANALYSIS_LAYER_MEETING_LAYER` (`meeting_id`, `layer`),
    KEY `IX_ANALYSIS_LAYER_MEETING_STATUS` (`meeting_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
