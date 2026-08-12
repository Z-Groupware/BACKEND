-- BIL-0: Store only billing-specific config values missing from metering.
-- Token pricing remains sourced from company_token_plan, and included storage remains
-- sourced from company_storage_plan, so a separate small table avoids duplicating
-- metering-owned pricing/cap columns on subscription.
CREATE TABLE `company_billing_config` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `company_id` BIGINT NOT NULL,
    `vat_included` BOOLEAN NOT NULL,
    `storage_overage_price_per_gb` INT NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    CONSTRAINT `uq_company_billing_config_company` UNIQUE (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
