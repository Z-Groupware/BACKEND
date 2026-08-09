CREATE TABLE company_token_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    plan_code VARCHAR(50) NOT NULL,
    monthly_token_pool BIGINT NOT NULL,
    base_fee INT NOT NULL,
    token_overage_price_per_1k INT NOT NULL,
    effective_from DATE NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uq_company_token_plan_company UNIQUE (company_id)
);
