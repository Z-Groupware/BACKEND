ALTER TABLE `action`
    ADD COLUMN `start_date` DATE NULL
        COMMENT '진행 시작일 — due_date와 달리 채울 원천이 없어 NULL 허용(2026-08-07 재설계)';
