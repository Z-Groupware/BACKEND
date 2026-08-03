ALTER TABLE meeting
    ADD CONSTRAINT fk_meeting_related_action
        FOREIGN KEY (related_action_id)
            REFERENCES action (id)
            ON DELETE SET NULL;