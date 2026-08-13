-- BIL-0: Keep the legacy billing_history table and add overage split for billing views.
-- The legacy status enum still contains FREE; billing responses must expose only PAID/FAILED.
ALTER TABLE `billing_history`
    ADD COLUMN `overage_amount` DECIMAL(12,2) NOT NULL DEFAULT 0.00 AFTER `amount`;
