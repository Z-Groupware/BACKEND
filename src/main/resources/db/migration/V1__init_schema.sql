-- =====================================================================
-- V1 : initial baseline schema (Module06 Ver 1.1.0)
-- ---------------------------------------------------------------------
-- 이 파일은 초기 전체 스키마를 담는 baseline 이다.
-- 운영 규칙 8-1(1파일 1논리변경)의 유일한 예외 = init/baseline.
-- V2 부터는 담당자별 버전 영역(V2.1.x ~ V2.6.x)에서 증분으로만 변경한다.
-- 원본 대비 수정: (1) payment_method.is_default DEFAULT BOOLEAN → FALSE (문법오류)
--               (2) 전 테이블 ENGINE=InnoDB / utf8mb4 명시(한글·FK)
-- =====================================================================

CREATE TABLE `company` (
    `id`         BIGINT       NOT NULL COMMENT '기업 ID',
    `code`       VARCHAR(20)  NOT NULL COMMENT '기업 코드 (로그인 1단계 키)',
    `name`       VARCHAR(100) NOT NULL COMMENT '기업명',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `transcript_chunk` (
    `id`         BIGINT   NOT NULL,
    `meeting_id` BIGINT   NOT NULL,
    `seq`        INT      NOT NULL COMMENT '청크 순번 (1부터)',
    `content`    TEXT     NOT NULL,
    `offset_ms`  INT      NULL COMMENT '회의 시작 기준 경과 ms',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `meeting` (
    `id`                 BIGINT       NOT NULL,
    `company_id`         BIGINT       NOT NULL COMMENT '테넌트 스코프용 의도적 반정규화',
    `project_id`         BIGINT       NOT NULL COMMENT '프로젝트 태그 (전 역할 필수)',
    `team_id`            BIGINT       NULL COMMENT 'OWNER 개설 회의는 NULL',
    `meeting_room_id`    BIGINT       NOT NULL,
    `host_member_id`     BIGINT       NOT NULL COMMENT '회의 담당자. 개설자 본인 고정',
    `title`              VARCHAR(200) NOT NULL,
    `status`             ENUM('SCHEDULED', 'IN_PROGRESS', 'DONE') NOT NULL DEFAULT 'SCHEDULED',
    `start_at`           DATETIME     NOT NULL,
    `end_at`             DATETIME     NOT NULL,
    `recording_consent`  BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '녹음 동의 안내 체크 → 알림 문구 분기',
    `started_at`         DATETIME     NULL COMMENT '실제 회의 시작 시각',
    `ended_at`           DATETIME     NULL COMMENT '실제 회의 종료 시각',
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `project` (
    `id`          BIGINT       NOT NULL,
    `company_id`  BIGINT       NOT NULL,
    `tag`         VARCHAR(30)  NOT NULL COMMENT '영문 전용. URL 식별자로 사용',
    `name`        VARCHAR(150) NOT NULL,
    `description` TEXT         NULL COMMENT '기획 상세',
    `color`       CHAR(7)      NOT NULL DEFAULT '#6B7280' COMMENT '태그 색상 HEX',
    `status`      ENUM('TODO', 'IN_PROGRESS', 'DONE') NOT NULL DEFAULT 'TODO',
    `due_date`    DATE         NOT NULL COMMENT '마감 기한. 액션 마감일 기본값의 원천',
    `created_by`  BIGINT       NULL COMMENT '개설자 (OWNER)',
    `deleted_at`  DATETIME     NULL COMMENT '용량 관리에서 완료 프로젝트 삭제 시',
    `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `summary_job` (
    `id`            BIGINT       NOT NULL,
    `meeting_id`    BIGINT       NOT NULL,
    `status`        ENUM('PENDING', 'PROCESSING', 'DONE', 'FAILED') NOT NULL DEFAULT 'PENDING',
    `retry_count`   INT          NOT NULL DEFAULT 0 COMMENT '재요약 요청 횟수 (파일 재제출 없음)',
    `requested_at`  DATETIME     NULL,
    `finished_at`   DATETIME     NULL,
    `error_message` VARCHAR(500) NULL,
    `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `handover_item` (
    `id`                   BIGINT   NOT NULL,
    `handover_id`          BIGINT   NOT NULL,
    `action_id`            BIGINT   NOT NULL,
    `successor_member_id`  BIGINT   NULL COMMENT 'DnD 로 지정된 인수자',
    `created_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `member` (
    `id`                    BIGINT       NOT NULL,
    `company_id`            BIGINT       NOT NULL,
    `team_id`               BIGINT       NULL COMMENT 'OWNER/ADMIN 은 팀 무소속 가능',
    `sub_team_id`           BIGINT       NULL,
    `job_position_id`       BIGINT       NULL,
    `email`                 VARCHAR(255) NOT NULL COMMENT '로그인 아이디 (기업 내 유일)',
    `password_hash`         VARCHAR(255) NOT NULL COMMENT '발급 시 1회 설정, 변경 기능 없음',
    `name`                  VARCHAR(50)  NOT NULL,
    `phone`                 VARCHAR(30)  NULL,
    `role`                  ENUM('OWNER', 'ADMIN', 'LEADER', 'MEMBER') NOT NULL DEFAULT 'MEMBER',
    `status`                ENUM('ACTIVE', 'ON_LEAVE', 'WAITING') NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE=재직 / ON_LEAVE=휴직 / WAITING=승인 대기',
    `annual_leave_granted`  DECIMAL(4, 1) NULL COMMENT '마이페이지 연차 조회용 (부여 일수)',
    `theme`                 ENUM('LIGHT', 'DARK', 'SYSTEM') NOT NULL DEFAULT 'SYSTEM',
    `joined_on`             DATE         NULL,
    `deleted_at`            DATETIME     NULL COMMENT '오프보딩 최종 승인 시 소프트 삭제',
    `created_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`            DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `meeting_attendee` (
    `meeting_id` BIGINT   NOT NULL,
    `member_id`  BIGINT   NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notice` (
    `id`         BIGINT       NOT NULL,
    `company_id` BIGINT       NOT NULL,
    `title`      VARCHAR(200) NOT NULL,
    `content`    TEXT         NOT NULL,
    `created_by` BIGINT       NULL COMMENT '작성자 (OWNER/ADMIN)',
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `meeting_room` (
    `id`             BIGINT       NOT NULL,
    `company_id`     BIGINT       NOT NULL,
    `name`           VARCHAR(100) NOT NULL,
    `location`       VARCHAR(150) NULL COMMENT '위치 (예: 박애관 421호)',
    `capacity`       INT          NOT NULL DEFAULT 0 COMMENT '수용 인원',
    `available_from` TIME         NOT NULL DEFAULT '09:00:00' COMMENT '이용 가능 시작',
    `available_to`   TIME         NOT NULL DEFAULT '18:00:00' COMMENT '이용 가능 종료',
    `deleted_at`     DATETIME     NULL,
    `created_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `billing_history` (
    `id`         BIGINT         NOT NULL,
    `company_id` BIGINT         NOT NULL,
    `billed_on`  DATE           NOT NULL,
    `plan`       ENUM('FREE','TEAM') NOT NULL,
    `seats`      INT            NOT NULL DEFAULT 0,
    `amount`     DECIMAL(12,2)  NOT NULL DEFAULT 0.00,
    `status`     ENUM('PAID','FREE','FAILED') NOT NULL,
    `created_at` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME       NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `recording` (
    `id`           BIGINT        NOT NULL,
    `meeting_id`   BIGINT        NOT NULL,
    `file_name`    VARCHAR(255)  NOT NULL,
    `file_url`     VARCHAR(1024) NOT NULL COMMENT '통합 녹음본 스토리지 참조',
    `file_size`    BIGINT        NOT NULL DEFAULT 0 COMMENT 'byte. /owner/storage 집계 원천',
    `duration_sec` INT           NULL,
    `created_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `project_attachment` (
    `id`          BIGINT        NOT NULL,
    `project_id`  BIGINT        NOT NULL,
    `file_name`   VARCHAR(255)  NOT NULL,
    `file_url`    VARCHAR(1024) NOT NULL COMMENT '오브젝트 스토리지 참조 (바이너리 미저장)',
    `file_size`   BIGINT        NOT NULL DEFAULT 0 COMMENT 'byte',
    `uploaded_by` BIGINT        NULL,
    `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `action` (
    `id`                 BIGINT       NOT NULL,
    `company_id`         BIGINT       NOT NULL COMMENT '테넌트 스코프용 의도적 반정규화',
    `project_id`         BIGINT       NOT NULL,
    `parent_action_id`   BIGINT       NULL COMMENT 'PERSONAL 일 때 상위 TEAM 액션',
    `source_meeting_id`  BIGINT       NULL COMMENT '출처 회의',
    `team_id`            BIGINT       NULL COMMENT 'TEAM 액션의 대상 팀',
    `assignee_member_id` BIGINT       NULL COMMENT 'PERSONAL 액션의 담당자',
    `action_type`        ENUM('TEAM', 'PERSONAL') NOT NULL,
    `title`              VARCHAR(200) NOT NULL,
    `description`        TEXT         NULL,
    `status`             ENUM('TODO', 'IN_PROGRESS', 'DONE') NOT NULL DEFAULT 'TODO',
    `due_date`           DATE         NOT NULL COMMENT '기본값=프로젝트 마감일. AI가 재지정 가능',
    `needs_review`       BOOLEAN      NOT NULL DEFAULT FALSE COMMENT '1=AI 확신 낮음, 검토 화면에서 수정 대상',
    `confirmed_at`       DATETIME     NULL COMMENT '담당자가 분배 확정한 시각',
    `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `project_team` (
    `project_id` BIGINT   NOT NULL,
    `team_id`    BIGINT   NOT NULL,
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `payment_method` (
    `id`         BIGINT      NOT NULL,
    `company_id` BIGINT      NOT NULL,
    `brand`      VARCHAR(30) NOT NULL COMMENT '카드 브랜드',
    `last4`      CHAR(4)     NOT NULL COMMENT '카드 끝 4자리',
    `expires_on` DATE        NOT NULL,
    `is_default` BOOLEAN     NOT NULL DEFAULT FALSE,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `job_position` (
    `id`           BIGINT      NOT NULL,
    `company_id`   BIGINT      NOT NULL,
    `name`         VARCHAR(50) NOT NULL COMMENT '직급명 (사원/대리/선임/팀장 등)',
    `default_role` ENUM('OWNER', 'ADMIN', 'LEADER', 'MEMBER') NOT NULL DEFAULT 'MEMBER' COMMENT '기업 설정의 직급↔권한 매핑 기본값',
    `sort_order`   INT         NOT NULL DEFAULT 0,
    `created_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `meeting_topic` (
    `id`              BIGINT       NOT NULL,
    `meeting_id`      BIGINT       NOT NULL,
    `parent_topic_id` BIGINT       NULL COMMENT 'SUB 일 때 소속 MAIN. 평면 운용 시 NULL',
    `topic_type`      ENUM('MAIN', 'SUB') NOT NULL COMMENT 'MAIN=대주제 / SUB=소주제',
    `content`         VARCHAR(300) NOT NULL,
    `sort_order`      INT          NOT NULL DEFAULT 0,
    `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `handover_approval` (
    `id`                  BIGINT       NOT NULL,
    `handover_id`         BIGINT       NOT NULL,
    `approver_member_id`  BIGINT       NOT NULL,
    `approval_type`       ENUM('MID', 'FINAL') NOT NULL COMMENT 'MID=리더 중간 / FINAL=오너·어드민 최종',
    `result`              ENUM('APPROVED', 'REJECTED') NOT NULL,
    `comment`             VARCHAR(500) NULL,
    `created_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `handover` (
    `id`                BIGINT       NOT NULL,
    `company_id`        BIGINT       NOT NULL COMMENT '테넌트 스코프용 의도적 반정규화',
    `member_id`         BIGINT       NOT NULL COMMENT '작성자 (인수인계 대상자)',
    `type`              ENUM('LEAVE', 'OFFBOARDING') NOT NULL COMMENT 'LEAVE=휴직 / OFFBOARDING=오프보딩',
    `status`            ENUM('SUBMITTED', 'MID_APPROVED', 'FINAL_APPROVED', 'REJECTED') NOT NULL DEFAULT 'SUBMITTED',
    `leave_start_date`  DATE         NULL COMMENT 'LEAVE 전용',
    `leave_end_date`    DATE         NULL COMMENT 'LEAVE 전용',
    `last_working_date` DATE         NULL COMMENT 'OFFBOARDING 전용',
    `reason`            VARCHAR(500) NULL,
    `memo`              TEXT         NULL,
    `created_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `sub_team` (
    `id`         BIGINT      NOT NULL,
    `team_id`    BIGINT      NOT NULL,
    `name`       VARCHAR(50) NOT NULL COMMENT '하위팀 (역할 태그, 리더 없음)',
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `action_checklist_item` (
    `id`         BIGINT       NOT NULL,
    `action_id`  BIGINT       NOT NULL,
    `content`    VARCHAR(300) NOT NULL,
    `is_done`    BOOLEAN      NOT NULL DEFAULT FALSE,
    `sort_order` INT          NOT NULL DEFAULT 0,
    `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `notification` (
    `id`                   BIGINT       NOT NULL,
    `recipient_member_id`  BIGINT       NOT NULL,
    `type`                 ENUM('MEETING_CREATED','MEETING_REMINDER') NOT NULL,
    `message`              VARCHAR(500) NOT NULL,
    `meeting_id`           BIGINT       NULL,
    `read_at`              DATETIME     NULL,
    `created_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `subscription` (
    `id`                  BIGINT   NOT NULL,
    `company_id`          BIGINT   NOT NULL,
    `plan`                ENUM('FREE', 'TEAM') NOT NULL DEFAULT 'FREE',
    `seats`               INT      NOT NULL DEFAULT 0 COMMENT '좌석 수 (과금 단위)',
    `status`              ENUM('ACTIVE', 'CANCELED') NOT NULL DEFAULT 'ACTIVE',
    `started_on`          DATE     NOT NULL,
    `current_period_end`  DATE     NULL,
    `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `team` (
    `id`         BIGINT      NOT NULL,
    `company_id` BIGINT      NOT NULL,
    `name`       VARCHAR(50) NOT NULL,
    `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ---------------------------------------------------------------------
-- Primary keys
-- ---------------------------------------------------------------------
ALTER TABLE `company`                ADD CONSTRAINT `PK_COMPANY`                PRIMARY KEY (`id`);
ALTER TABLE `transcript_chunk`       ADD CONSTRAINT `PK_TRANSCRIPT_CHUNK`       PRIMARY KEY (`id`);
ALTER TABLE `meeting`                ADD CONSTRAINT `PK_MEETING`                PRIMARY KEY (`id`);
ALTER TABLE `project`                ADD CONSTRAINT `PK_PROJECT`                PRIMARY KEY (`id`);
ALTER TABLE `summary_job`            ADD CONSTRAINT `PK_SUMMARY_JOB`            PRIMARY KEY (`id`);
ALTER TABLE `handover_item`          ADD CONSTRAINT `PK_HANDOVER_ITEM`          PRIMARY KEY (`id`);
ALTER TABLE `member`                 ADD CONSTRAINT `PK_MEMBER`                 PRIMARY KEY (`id`);
ALTER TABLE `meeting_attendee`       ADD CONSTRAINT `PK_MEETING_ATTENDEE`       PRIMARY KEY (`meeting_id`, `member_id`);
ALTER TABLE `notice`                 ADD CONSTRAINT `PK_NOTICE`                 PRIMARY KEY (`id`);
ALTER TABLE `meeting_room`           ADD CONSTRAINT `PK_MEETING_ROOM`           PRIMARY KEY (`id`);
ALTER TABLE `billing_history`        ADD CONSTRAINT `PK_BILLING_HISTORY`        PRIMARY KEY (`id`);
ALTER TABLE `recording`              ADD CONSTRAINT `PK_RECORDING`              PRIMARY KEY (`id`);
ALTER TABLE `project_attachment`     ADD CONSTRAINT `PK_PROJECT_ATTACHMENT`     PRIMARY KEY (`id`);
ALTER TABLE `action`                 ADD CONSTRAINT `PK_ACTION`                 PRIMARY KEY (`id`);
ALTER TABLE `project_team`           ADD CONSTRAINT `PK_PROJECT_TEAM`           PRIMARY KEY (`project_id`, `team_id`);
ALTER TABLE `payment_method`         ADD CONSTRAINT `PK_PAYMENT_METHOD`         PRIMARY KEY (`id`);
ALTER TABLE `job_position`           ADD CONSTRAINT `PK_JOB_POSITION`           PRIMARY KEY (`id`);
ALTER TABLE `meeting_topic`          ADD CONSTRAINT `PK_MEETING_TOPIC`          PRIMARY KEY (`id`);
ALTER TABLE `handover_approval`      ADD CONSTRAINT `PK_HANDOVER_APPROVAL`      PRIMARY KEY (`id`);
ALTER TABLE `handover`               ADD CONSTRAINT `PK_HANDOVER`               PRIMARY KEY (`id`);
ALTER TABLE `sub_team`               ADD CONSTRAINT `PK_SUB_TEAM`               PRIMARY KEY (`id`);
ALTER TABLE `action_checklist_item`  ADD CONSTRAINT `PK_ACTION_CHECKLIST_ITEM`  PRIMARY KEY (`id`);
ALTER TABLE `notification`           ADD CONSTRAINT `PK_NOTIFICATION`           PRIMARY KEY (`id`);
ALTER TABLE `subscription`           ADD CONSTRAINT `PK_SUBSCRIPTION`           PRIMARY KEY (`id`);
ALTER TABLE `team`                   ADD CONSTRAINT `PK_TEAM`                   PRIMARY KEY (`id`);

-- ---------------------------------------------------------------------
-- AUTO_INCREMENT (단일 컬럼 `id` PK 전용)
-- ---------------------------------------------------------------------
-- ERD 원본에는 없으나, JPA @GeneratedValue(strategy = IDENTITY) 사용을 위해 부여한다.
-- CREATE TABLE 시점에는 넣을 수 없다(auto 컬럼은 반드시 키여야 하는데 PK를 뒤에서 ALTER 로
-- 붙이는 구조라 "there can be only one auto column and it must be defined as a key" 로 실패).
-- 따라서 PK 부여 이후 MODIFY 로 부여한다.
-- 복합 PK 조인 테이블(meeting_attendee, project_team)은 대상이 아니다.
ALTER TABLE `company`               MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '기업 ID';
ALTER TABLE `transcript_chunk`      MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `meeting`               MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `project`               MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `summary_job`           MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `handover_item`         MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `member`                MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `notice`                MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `meeting_room`          MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `billing_history`       MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `recording`             MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `project_attachment`    MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `action`                MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `payment_method`        MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `job_position`          MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `meeting_topic`         MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `handover_approval`     MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `handover`              MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `sub_team`              MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `action_checklist_item` MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `notification`          MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `subscription`          MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;
ALTER TABLE `team`                  MODIFY `id` BIGINT NOT NULL AUTO_INCREMENT;

-- ---------------------------------------------------------------------
-- Foreign keys (원본 정의분만. 나머지 암시적 FK는 후속 V2.x에서 담당자가 추가)
-- ---------------------------------------------------------------------
ALTER TABLE `meeting_attendee` ADD CONSTRAINT `FK_meeting_TO_meeting_attendee_1` FOREIGN KEY (`meeting_id`) REFERENCES `meeting` (`id`);
ALTER TABLE `meeting_attendee` ADD CONSTRAINT `FK_member_TO_meeting_attendee_1`  FOREIGN KEY (`member_id`)  REFERENCES `member` (`id`);
ALTER TABLE `project_team`     ADD CONSTRAINT `FK_project_TO_project_team_1`     FOREIGN KEY (`project_id`) REFERENCES `project` (`id`);
ALTER TABLE `project_team`     ADD CONSTRAINT `FK_team_TO_project_team_1`        FOREIGN KEY (`team_id`)    REFERENCES `team` (`id`);
