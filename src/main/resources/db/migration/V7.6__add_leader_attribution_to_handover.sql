ALTER TABLE handover
    ADD COLUMN is_leader_handover TINYINT(1) NOT NULL DEFAULT 0,
    ADD COLUMN new_leader_id BIGINT NULL,
    ADD COLUMN new_leader_name_snap VARCHAR(255) NULL,
    ADD COLUMN new_leader_position_snap VARCHAR(255) NULL,
    ADD COLUMN attributed_at DATETIME(6) NULL;
