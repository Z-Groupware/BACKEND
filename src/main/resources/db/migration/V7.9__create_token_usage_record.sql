CREATE TABLE token_usage_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    team_id BIGINT NULL,
    meeting_id BIGINT NOT NULL,
    job_id VARCHAR(100) NOT NULL,
    input_tokens INT NOT NULL,
    output_tokens INT NOT NULL,
    total_tokens INT NOT NULL,
    model VARCHAR(100) NOT NULL,
    recorded_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_token_usage_job_id UNIQUE (job_id),
    INDEX idx_token_usage_company_recorded (company_id, recorded_at),
    INDEX idx_token_usage_company_team (company_id, team_id)
);
