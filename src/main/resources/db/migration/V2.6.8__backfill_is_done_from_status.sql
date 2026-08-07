-- V2.6.6이 is_done을 기본값(FALSE)만 넣고 기존 행을 백필하지 않았다. Action.reconstitute()는
-- 더 이상 action.status를 보지 않고 is_done·start_date로만 상태를 파생하므로, 안 채우면
-- 이미 완료돼 있던 행이 조용히 "할일"로 보인다(2026-08-07, CodeRabbit 지적).
--
-- 이미 push된 V2.6.6을 직접 고치지 않고 새 파일로 보완한다(migration 수정 금지 원칙).
UPDATE `action` SET `is_done` = TRUE WHERE `status` = 'DONE';
