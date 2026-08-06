-- =====================================================================
-- V5.14 : meeting_assignment_tuple — L6(모순 검사) · L7(자동확정 게이트) 판정 자리
-- ---------------------------------------------------------------------
-- 파이프라인의 마지막 두 계층이 여기서 끝난다. L7 이 가른 두 묶음이 곧 검토 화면의
-- 「AI 확신도 높음」 · 「AI 확인 필요」다.
--
-- 두 계층을 한 ALTER 로 넣는 이유 — L7 이 L6 의 결과를 읽는다(모순이 있으면 자동확정
-- 하지 않는다). 나눠 넣으면 앞 마이그레이션만 적용된 순간에 L7 이 참조할 컬럼이 없는
-- 상태가 생긴다. ALTER 한 문장이므로 MySQL 8 의 atomic DDL 로 통째로 적용되거나 통째로
-- 실패한다(DB_MIGRATION_RULES 8-2).
--
-- ⚠ 모델이 스스로 말한 확신도를 저장하지 않는다
-- 자기보고 신뢰도는 실제 정확도와 맞지 않는다(LLM 에 물으면 85~95 에 몰린다).
-- **코드로 판정한 신호 넷을 그대로 컬럼에 남긴다** — 사람이 검증할 수 있어야 하고,
-- 나중에 어느 조건이 자동확정 오류를 만드는지 세려면 신호가 개별로 남아 있어야 한다.
-- 백분율을 만들어 내려주지 않는다(명세 RVW-01 처리 정책).
--
-- 그래서 gate_signals 를 JSON 한 덩어리로 담지 않고 컬럼 넷으로 편다. 덩어리로 두면
-- "assigneeSource 조건에서 떨어진 건이 몇 %인가"를 묻는 순간 전부 파싱해야 한다.
--
-- ── NULL 의 뜻 ────────────────────────────────────────────────────────
-- V5.13 의 verify_agree 와 같은 규칙이다. NULL 은 **그 계층이 이 행을 아직 보지 않았다**
-- 이고, FALSE(걸렸다)와 다르다. 뭉치면 계층이 통째로 안 돈 회의가 전부 검토 대상으로
-- 보인다. 그래서 DEFAULT 를 두지 않는다.
--
-- conflicts 는 쉼표로 이어 붙인 모순 코드다(verify_disagreement_fields 와 같은 방식).
-- NULL 이 "모순 없음"이고, 검사 여부는 conflict_checked_at 이 가른다 — 둘을 한 컬럼으로
-- 합치면 "검사했는데 깨끗함"과 "아직 안 봄"이 같은 값이 된다.
--
-- ⚠ auto_confirmed=TRUE 가 "분배됐다"는 뜻이 아니다
-- 자동 확정 건도 **분배 전까지는 아무 데도 가 있지 않다**(명세 RVW-01). 실제 분배는
-- RVW-05 가 사람의 확인을 받아 하고, 그때 action_id 가 채워진다. 이 컬럼은 검토 화면의
-- 두 묶음을 가르는 값일 뿐이다.
-- =====================================================================

ALTER TABLE `meeting_assignment_tuple`
    -- ── L6 · 규칙·모순 검사 ──────────────────────────────────────────
    ADD COLUMN `conflicts`               VARCHAR(200) NULL COMMENT 'L6 검출 모순 코드(쉼표 구분). NULL=모순 없음' AFTER `verified_at`,
    ADD COLUMN `conflict_checked_at`     DATETIME     NULL COMMENT 'L6 수행 시각. NULL=미수행 — conflicts IS NULL 과 뜻이 다르다' AFTER `conflicts`,

    -- ── L7 · 자동확정 게이트(4조건) ──────────────────────────────────
    -- 넷을 **전부** 만족하고 L6 모순이 없을 때만 TRUE 다(명세 「자동 확정은 넷을 전부
    -- 만족할 때만」 + 모순 검사를 지난 것). 게이트는 조이는 방향으로만 더한다.
    ADD COLUMN `gate_auto_confirmed`     BOOLEAN      NULL COMMENT 'NULL=L7 미수행. TRUE=「AI 확신도 높음」 묶음' AFTER `conflict_checked_at`,
    ADD COLUMN `gate_has_evidence`       BOOLEAN      NULL COMMENT '조건1 — 근거 발화 id 가 있다' AFTER `gate_auto_confirmed`,
    ADD COLUMN `gate_assignee_in_roster` BOOLEAN      NULL COMMENT '조건2 — 담당자가 참석자 명단 안이다(unknown_person 아님)' AFTER `gate_has_evidence`,
    ADD COLUMN `gate_assignee_source_ok` BOOLEAN      NULL COMMENT '조건3 — 명시적 호명이거나, 1인칭이면서 근거 발화의 화자가 확정됐다' AFTER `gate_assignee_in_roster`,
    ADD COLUMN `gate_views_agree`        BOOLEAN      NULL COMMENT '조건4 — L5 두 관점이 일치했다(verify_agree)' AFTER `gate_assignee_source_ok`,
    ADD COLUMN `gated_at`                DATETIME     NULL COMMENT 'L7 수행 시각' AFTER `gate_views_agree`,

    -- 검토 화면이 두 묶음을 나눠 읽는 경로. IX_ASSIGNMENT_TUPLE_VERIFY(meeting_id,
    -- verify_agree)와 짝이지만 묻는 것이 다르다 — 이쪽은 "자동확정인가"이고 그게 RVW-01
    -- 응답의 그룹핑 기준이다.
    ADD KEY `IX_ASSIGNMENT_TUPLE_GATE` (`meeting_id`, `gate_auto_confirmed`);
