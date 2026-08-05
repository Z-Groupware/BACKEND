-- =====================================================================
-- V5.11 : quality_gold_set — 정답지 동결 (QLTY-01 등록 · QLTY-02 지표 산출)
-- ---------------------------------------------------------------------
-- 측정 장치는 데이터가 쌓이기 전에 있어야 한다. gold set 이 없으면 프롬프트를
-- 바꿔도 나아졌는지 알 수 없고, 정확도 개선이 감으로만 남는다.
--
-- 동결(freeze)이 핵심이다. 나중에 라벨을 손대면 이전 측정치와 비교가 불가능해진다.
--
-- 그래서 회의당 1건이 아니라 **버전으로 누적**한다. 회의당 1건(UNIQUE)으로 두면 다시
-- 라벨링할 때 기존 동결 행을 지워야 하고, 그 순간 과거 측정에 쓰인 정답지가 사라져
-- "지난주 precision 0.82" 를 재현할 수 없게 된다 — 동결의 목적 자체가 무너진다.
--   재라벨링  → version + 1 로 새 행 INSERT (기존 행은 그대로 둔다)
--   측정 결과 → 사용한 quality_gold_set.id 를 함께 기록한다(어느 정답지로 잰 수치인가)
--
-- ⚠️ MySQL 로는 "동결 행 UPDATE·DELETE 금지"를 제약으로 표현할 수 없다(트리거가 필요하다).
--    스키마는 버전 누적을 가능하게만 하고, 기존 행을 건드리지 않는 것은 QLTY-01 구현이 지킨다.
--
-- 무작위로 뽑는다 — 잘 된 회의만 고르면 지표가 실제보다 좋게 나온다.
-- 3주 스프린트에서는 5~10건으로 시작한다.
--
-- 지표 셋의 역할이 다르다는 것을 기록해 둔다.
--   precision / recall     주 품질 지표 (이 정답지 대비)
--   autoConfirmErrorRate   자동 확정 게이트 검증
--   needsReviewRate        비용 지표. 품질 목표로 삼으면 임계값을 낮춰 숫자를
--                          맞추려는 유인이 생기고 '기권 우선' 원칙과 충돌한다
-- =====================================================================

CREATE TABLE `quality_gold_set` (
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id`       BIGINT       NOT NULL,
    `meeting_id`       BIGINT       NOT NULL,
    `version`          INT          NOT NULL DEFAULT 1 COMMENT '재라벨링마다 +1. 기존 버전은 지우지 않는다 — 과거 측정치의 정답지다',
    `labeled_actions`  JSON         NOT NULL COMMENT '사람이 전량 라벨링한 정답 tuple 목록 (담당자·기한·근거 발화)',
    `labeled_items`    JSON         NULL COMMENT '결정·논의·블로커 정답. L3·L3.5 채점용',
    `frozen_at`        DATETIME     NOT NULL COMMENT '동결 시각. 이후 라벨을 고치면 이전 측정치와 비교가 깨진다',
    `frozen_by`        BIGINT       NOT NULL COMMENT '라벨링한 member_id',
    `note`             VARCHAR(500) NULL COMMENT '선정 사유 · 특이사항 (예: 참석자 6명, 자막 2명 미사용)',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_QUALITY_GOLD_SET_MEETING_VERSION` (`meeting_id`, `version`) COMMENT '회의당 버전 누적. 재라벨링은 새 버전 INSERT 다',
    KEY `IX_QUALITY_GOLD_SET_COMPANY` (`company_id`),
    -- 어느 버전이 최신인지 한 번에 찾는다(측정할 때는 보통 최신 버전을 쓴다).
    KEY `IX_QUALITY_GOLD_SET_LATEST` (`meeting_id`, `version` DESC),
    CONSTRAINT `CK_QUALITY_GOLD_SET_VERSION` CHECK (`version` >= 1)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
