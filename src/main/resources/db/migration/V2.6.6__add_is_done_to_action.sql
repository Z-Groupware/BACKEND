ALTER TABLE `action`
    ADD COLUMN `is_done` BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '완료 여부 — 보드 상태(status)는 이 값과 start_date로부터 파생된다(2026-08-07 재설계)';
