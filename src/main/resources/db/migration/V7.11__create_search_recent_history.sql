-- =====================================================================
-- V7.11 : search recent query/view history
-- ---------------------------------------------------------------------
-- 검색 빈 상태 대시보드(SR-3)에서 사용자별 최근 검색어와 최근 본 항목을 제공한다.
-- 두 테이블 모두 company_id 를 직접 보유해 테넌트 스코프를 저장소 WHERE 절에서 강제한다.
-- =====================================================================

CREATE TABLE `search_recent_query` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id`  BIGINT       NOT NULL COMMENT '테넌트 스코프',
    `member_id`   BIGINT       NOT NULL COMMENT '검색어를 저장한 멤버',
    `query_text`  VARCHAR(255) NOT NULL COMMENT 'trim 처리된 검색어 원문',
    `searched_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최근 검색 시각',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_SEARCH_RECENT_QUERY_MEMBER_TEXT` (`company_id`, `member_id`, `query_text`),
    KEY `IX_SEARCH_RECENT_QUERY_MEMBER_TIME` (`company_id`, `member_id`, `searched_at`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `search_recent_view` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id`  BIGINT       NOT NULL COMMENT '테넌트 스코프',
    `member_id`   BIGINT       NOT NULL COMMENT '항목을 열람한 멤버',
    `entity_type` ENUM('MEETING','ACTION','PROJECT','PERSON') NOT NULL COMMENT '검색 결과 타입',
    `entity_id`   BIGINT       NOT NULL COMMENT '검색 결과 타입별 원본 엔티티 ID',
    `viewed_at`   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '최근 열람 시각',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `UK_SEARCH_RECENT_VIEW_MEMBER_ENTITY` (`company_id`, `member_id`, `entity_type`, `entity_id`),
    KEY `IX_SEARCH_RECENT_VIEW_MEMBER_TIME` (`company_id`, `member_id`, `viewed_at`, `id`),
    KEY `IX_SEARCH_RECENT_VIEW_ENTITY` (`company_id`, `entity_type`, `entity_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
