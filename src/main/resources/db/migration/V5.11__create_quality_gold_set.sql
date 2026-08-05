-- =====================================================================
-- V5.11 : quality_gold_set — 정답지 동결 (QLTY-01 등록 · QLTY-02 지표 산출)
-- ---------------------------------------------------------------------
-- 측정 장치는 데이터가 쌓이기 전에 있어야 한다. gold set 이 없으면 프롬프트를
-- 바꿔도 나아졌는지 알 수 없고, 정확도 개선이 감으로만 남는다.
--
-- 동결(freeze)이 핵심이다. 나중에 라벨을 손대면 이전 측정치와 비교가 불가능해진다.
-- frozen_at 이후 labeled_* 는 갱신하지 않는다. 회의당 1건(UNIQUE)으로 두어 QLTY-02
-- 집계에서 어느 정답지를 쓴 것인지 모호해지지 않게 한다.
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
    `labeled_actions`  JSON         NOT NULL COMMENT '사람이 전량 라벨링한 정답 tuple 목록 (담당자·기한·근거 발화)',
    `labeled_items`    JSON         NULL COMMENT '결정·논의·블로커 정답. L3·L3.5 채점용',
    `frozen_at`        DATETIME     NOT NULL COMMENT '동결 시각. 이후 라벨을 고치면 이전 측정치와 비교가 깨진다',
    `frozen_by`        BIGINT       NOT NULL COMMENT '라벨링한 member_id',
    `note`             VARCHAR(500) NULL COMMENT '선정 사유 · 특이사항 (예: 참석자 6명, 자막 2명 미사용)',
    `created_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_QUALITY_GOLD_SET_MEETING` (`meeting_id`) COMMENT '회의당 1건. 재라벨링은 기존 행을 지우고 새로 등록한다',
    KEY `IX_QUALITY_GOLD_SET_COMPANY` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
