ALTER TABLE meeting
    ADD COLUMN related_action_id BIGINT NULL
        COMMENT '이 회의가 참조하는 팀 액션 ID',
    ADD CONSTRAINT fk_meeting_related_action
        FOREIGN KEY (related_action_id)
        REFERENCES action (id)
        ON DELETE SET NULL;