ALTER TABLE meeting
    ADD COLUMN related_action_id BIGINT NULL
        COMMENT '이 회의가 참조하는 팀 액션 ID';