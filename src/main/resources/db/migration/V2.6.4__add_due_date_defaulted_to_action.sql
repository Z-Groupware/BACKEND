ALTER TABLE `action`
    ADD COLUMN `due_date_defaulted` BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT 'TRUE=AI가 기한을 정하지 않아 프로젝트 마감일로 채움, FALSE=AI가 직접 판단한 기한';
