-- =====================================================================
-- V5.7 : meeting_summary — 회의 요약 본문 (ANLZ-03 조회 · ANLZ-04 수정)
-- ---------------------------------------------------------------------
-- ⚠ 소유권 미결. 이 테이블과 V5.8(meeting_decision)을 A(이태연)가 만드는지
--    D(모성진)가 만드는지 아직 정해지지 않았다. D 레인에서 같은 이름으로
--    CREATE TABLE 이 들어오면 마이그레이션이 충돌해 부팅이 막힌다
--    (V1 baseline이 handover 테이블을 V7.x로 넘긴 것과 같은 상황).
--    머지 전에 반드시 확정할 것.
--
-- 요약은 회의 단위 메타(본문·편집 흔적·생성 모델)만 여기 두고, 주제별 결정·
-- 논의·블로커 항목은 V5.8 로 분리한다. ANLZ-03 응답의 topics[].items[] 가
-- 그쪽이다.
--
-- edited_at 이 NULL 이 아니면 사람이 손댄 요약이다. ANLZ-04 수정 내역은
-- review_log 에 layer='L3' 으로도 남는다 — 액션만 라벨이 아니라 요약도 라벨이다.
-- =====================================================================

CREATE TABLE `meeting_summary` (
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `company_id`          BIGINT      NOT NULL COMMENT '테넌트 스코프용 의도적 반정규화 (baseline meeting과 동일 방식)',
    `meeting_id`          BIGINT      NOT NULL,
    `overview`            TEXT        NULL COMMENT '회의 전체 요약 본문',
    `edited_by_member_id` BIGINT      NULL COMMENT 'ANLZ-04로 수정한 사람',
    `edited_at`           DATETIME    NULL COMMENT 'NULL이면 AI 생성 원본 그대로',
    `model_name`          VARCHAR(60) NULL,
    `prompt_version`      VARCHAR(20) NULL,
    `created_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_MEETING_SUMMARY_MEETING` (`meeting_id`) COMMENT '회의당 요약 1건. ANLZ-01 강제 재실행은 갱신이다',
    -- V5.8 meeting_decision 이 (meeting_summary_id, meeting_id) 복합 FK 로 참조할 대상.
    -- id 만 참조하면 자식이 '다른 회의의 요약'을 가리키면서 자기 meeting_id 는 딴 값을 갖는 조합이
    -- 만들어진다. FK 대상이 되려면 참조 컬럼 조합에 유니크 인덱스가 있어야 한다.
    UNIQUE KEY `UK_MEETING_SUMMARY_ID_MEETING` (`id`, `meeting_id`),
    KEY `IX_MEETING_SUMMARY_COMPANY` (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
