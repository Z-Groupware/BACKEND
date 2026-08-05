ALTER TABLE `action`
    ADD COLUMN `pending_handover_ack` BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'TRUE=인계로 담당자가 바뀌어 아직 확인 안 한 상태, acknowledge 호출 시 FALSE로';
