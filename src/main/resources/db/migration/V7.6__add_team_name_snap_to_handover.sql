ALTER TABLE `handover`
  ADD COLUMN `team_name_snap` VARCHAR(255) NULL COMMENT 'Team name snapshot at handover creation time'
  AFTER `team_id`;
