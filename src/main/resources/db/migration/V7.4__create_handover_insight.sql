CREATE TABLE `handover_insight` (
    `id`          BIGINT NOT NULL,
    `handover_id` BIGINT NOT NULL,
    `action_id`   BIGINT NULL,
    `kind`        ENUM('OWNERSHIP','ORPHAN_ALERT','ASK_WHOM','CONTEXT_TIMELINE') NOT NULL,
    `payload`     JSON NOT NULL,
    `sort_order`  INT NOT NULL DEFAULT 0,
    `created_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE `handover_insight`
    ADD CONSTRAINT `PK_HANDOVER_INSIGHT` PRIMARY KEY (`id`);

ALTER TABLE `handover_insight`
    MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;

CREATE INDEX `IX_HANDOVER_INSIGHT_HANDOVER_KIND_SORT`
    ON `handover_insight` (`handover_id`, `kind`, `sort_order`);

CREATE INDEX `IX_HANDOVER_INSIGHT_ACTION_KIND`
    ON `handover_insight` (`action_id`, `kind`);
