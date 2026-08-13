ALTER TABLE `action`
    ADD COLUMN `planned_start_date` DATE NULL
        COMMENT '예정 시작일 — start_date(실제 착수일, 상태 파생의 원천)와 분리된 순수 표시값. 상태에 영향 없음(2026-08-12, 이태연·홍근 합의)';
