-- BIL-0: Store the PG billing key server-side only and enforce one card per company.
-- If legacy rows contain more than one card for a company, keep the newest row so
-- the UNIQUE(company_id) contract can be applied deterministically.
ALTER TABLE `payment_method`
    ADD COLUMN `billing_key` VARCHAR(255) NULL AFTER `expires_on`;

DELETE pm_old
FROM `payment_method` pm_old
INNER JOIN `payment_method` pm_new
    ON pm_old.`company_id` = pm_new.`company_id`
   AND pm_old.`id` < pm_new.`id`;

ALTER TABLE `payment_method`
    ADD CONSTRAINT `uq_payment_method_company` UNIQUE (`company_id`);
