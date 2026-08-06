-- =====================================================================
-- V5.13 : meeting_assignment_tuple — L5(관점 다변화 검증) 판정 자리
-- ---------------------------------------------------------------------
-- L4 가 뽑은 tuple 이 맞는지 L5 가 관점을 바꿔 다시 묻고, 그 결과를 여기 적는다.
-- V5.12 대기실에 "검증받았는가"를 기록하는 칸이 없어 L5 를 붙일 수 없었다.
--
-- ⚠ 왜 needs_review 컬럼을 만들지 않는가
-- 명세는 "불일치는 다수결로 덮지 않고 needs_review 로 넘긴다"고 말하지만, 그 값은
-- verify_agree 에서 그대로 파생된다(needs_review = verify_agree IS FALSE). 파생값을
-- 컬럼으로 따로 두면 둘이 어긋나는 상태가 만들어지고, 어긋났을 때 어느 쪽이 진짜인지
-- 아무도 모른다. 검토 대상 목록은 verify_agree 로 뽑는다.
--   (action.needs_review 는 V2.6.3 에서 드롭됐고 review_status 로 넓힐지가 아직 논의
--    중이다 — 여기서 그 이름을 미리 쓰면 그 논의의 결론을 선점하게 된다.)
--
-- ⚠ NULL 이 "검증하지 않음"이다
-- verify_agree 는 3-상태다 — TRUE(두 관점 일치) · FALSE(갈렸거나 한쪽 실패) ·
-- NULL(L5 가 이 행을 아직 보지 않음). NULL 을 FALSE 로 뭉치면 "검증에서 걸린 것"과
-- "검증을 안 한 것"이 같아지고, L5 가 통째로 안 돈 회의가 전부 검토 대상으로 보인다.
-- 그래서 DEFAULT 를 두지 않는다.
--
-- ⚠ verify_agree=FALSE 는 tuple 이 틀렸다는 뜻이 아니다
-- "두 관점이 갈렸다" 또는 "한 관점이 죽었다"이다(L5 는 한쪽만 실패해도 안전한 쪽으로
-- 접어 agree=false 를 준다). 그래서 이 값으로 행을 지우지 않는다 — 사람이 볼 대상으로
-- 표시할 뿐이고, 판정을 무엇으로 쓸지는 L7(자동확정 게이트 4조건)이 정한다.
--
-- verify_disagreement_fields 는 쉼표로 이어 붙인 필드명이다. JSON 타입을 쓰지 않는 이유는
-- 값의 상한이 정해져 있기 때문이다 — 비교 필드가 셋(title · assigneeCandidatePersonId ·
-- dueDate)이고 여기에 재현 실패 표시(notReproduced) 하나가 더 붙는 것이 전부다.
-- 지금 이 값을 읽는 것은 사람뿐이고, 필드 단위로 질의할 일이 생기면 그때 옮긴다.
--
-- verify_model_name · verify_prompt_version 을 L4 의 model_name 과 따로 두는 이유 —
-- 두 계층은 다른 프롬프트를 쓰고, 나중에 특화 모델로 갈릴 수도 있다. 한 칸을 공유하면
-- 검증이 덮어써서 "이 tuple 을 뽑은 모델"이 사라진다. 정확도 변화를 무엇 탓으로 돌릴지의
-- 키가 그 값이다.
--
-- 컬럼 추가는 ALTER 한 문장이다(1파일 1논리변경 · MySQL 은 DDL 롤백이 안 된다).
-- V5.7·V5.8 의 updated_at ON UPDATE 보완은 다른 테이블의 일이므로 여기 얹지 않는다.
-- =====================================================================

ALTER TABLE `meeting_assignment_tuple`
    ADD COLUMN `verify_agree`               BOOLEAN      NULL COMMENT 'L5 두 관점 일치 여부. NULL=미검증, FALSE=검토 대상' AFTER `prompt_version`,
    ADD COLUMN `verify_disagreement_fields` VARCHAR(200) NULL COMMENT '갈린 필드명(쉼표 구분). notReproduced=좁은 시야에서 재현 안 됨' AFTER `verify_agree`,
    ADD COLUMN `verify_verdict`             ENUM('ACCEPT', 'REJECT') NULL COMMENT 'VERIFY 관점 판정. NULL=그 관점이 실패했다' AFTER `verify_disagreement_fields`,
    ADD COLUMN `verify_reason`              VARCHAR(500) NULL COMMENT 'VERIFY 관점이 그렇게 판정한 근거. 게이트를 조일지 풀지의 재료' AFTER `verify_verdict`,
    ADD COLUMN `verify_model_name`          VARCHAR(60)  NULL COMMENT 'L4 의 model_name 과 별도. 검증이 추출을 덮지 않게 한다' AFTER `verify_reason`,
    ADD COLUMN `verify_prompt_version`      VARCHAR(20)  NULL AFTER `verify_model_name`,
    ADD COLUMN `verified_at`                DATETIME     NULL COMMENT 'L5 가 이 행을 판정한 시각. NULL=미검증' AFTER `verify_prompt_version`,
    -- 검토 대기 목록 조회용. IX_ASSIGNMENT_TUPLE_PENDING(meeting_id, action_id)이 "분배됐는가"를
    -- 보는 것과 짝이다 — 이쪽은 "사람이 봐야 하는가"를 본다. 둘은 다른 질문이고 함께 걸리는
    -- 조건도 아니라(검토 대상이면서 이미 분배된 행이 정상적으로 존재한다) 인덱스를 나눈다.
    ADD KEY `IX_ASSIGNMENT_TUPLE_VERIFY` (`meeting_id`, `verify_agree`);
