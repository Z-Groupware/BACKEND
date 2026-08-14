-- BIL-0: Extend the legacy subscription table in place.
-- Identity reads this table through SubscriptionRefEntity with status as String,
-- so adding billing statuses is compatible while preserving legacy CANCELED rows.
ALTER TABLE `subscription`
    MODIFY `status` ENUM('ACTIVE', 'CANCELED', 'CANCELING', 'UNPAID', 'EXPIRED') NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN `current_period_start` DATE NULL AFTER `started_on`,
    ADD COLUMN `next_billing_date` DATE NULL AFTER `current_period_end`,
    ADD COLUMN `carried_overage_amount` INT NOT NULL DEFAULT 0 AFTER `next_billing_date`;

UPDATE `subscription`
SET `current_period_start` = `started_on`
WHERE `current_period_start` IS NULL;

ALTER TABLE `subscription`
    MODIFY `current_period_start` DATE NOT NULL;
