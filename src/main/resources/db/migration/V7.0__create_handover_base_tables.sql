-- =====================================================================
-- V7.0 : E(인수인계·휴직) 도메인 베이스 스키마 (박종준 레인 V7.x의 create baseline)
-- ---------------------------------------------------------------------
-- handover / handover_item 두 테이블 생성. 이후 증분(V7.1 version, V7.2 final_approver,
-- V7.3 rollback tracking)이 이 위에 ALTER로 얹힌다. 컬럼은 JPA 엔티티(HandoverJpaEntity/
-- HandoverItemJpaEntity)의 V7.1~7.3 반영 전 상태와 일치.
-- 부모+자식(handover↔handover_item) 단일 논리단위라 한 파일에 담는다(1파일-1변경의 create 예외).
-- =====================================================================

CREATE TABLE `handover` (
    `id`                              BIGINT       NOT NULL AUTO_INCREMENT,
    `writer_member_id`                BIGINT       NOT NULL COMMENT '작성자(퇴사/휴직 당사자) member id',
    `team_id`                         BIGINT       NOT NULL COMMENT '작성자 소속 팀 id',
    `handover_type`                   VARCHAR(20)  NOT NULL COMMENT 'VACATION | OFFBOARDING',
    `status`                          VARCHAR(20)  NOT NULL COMMENT 'SUBMITTED|REASSIGNED|FINALIZED|REJECTED',
    `leave_start_at`                  DATETIME     NULL COMMENT '휴직 시작(VACATION만)',
    `leave_end_at`                    DATETIME     NULL COMMENT '휴직 종료(VACATION만)',
    `last_working_day`                DATE         NULL COMMENT '마지막 근무일(OFFBOARDING만)',
    `writer_name_snap`                VARCHAR(255) NOT NULL COMMENT '작성자 이름 스냅샷',
    `writer_position_snap`            VARCHAR(255) NOT NULL COMMENT '작성자 직급 스냅샷',
    `intermediate_approver_id`        BIGINT       NULL COMMENT '중간 승인자(팀리더) member id',
    `intermediate_approver_name_snap` VARCHAR(255) NULL COMMENT '중간 승인자 이름 스냅샷',
    `intermediate_approved_at`        DATETIME     NULL COMMENT '중간 승인 시각',
    `reject_reason`                   VARCHAR(255) NULL COMMENT '반려 사유',
    `finalized_at`                    DATETIME     NULL COMMENT '최종 승인 시각',
    `created_at`                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `IX_HANDOVER_WRITER_STATUS` ON `handover` (`writer_member_id`, `status`);
CREATE INDEX `IX_HANDOVER_TEAM_STATUS`   ON `handover` (`team_id`, `status`);

CREATE TABLE `handover_item` (
    `id`                        BIGINT       NOT NULL AUTO_INCREMENT,
    `handover_id`               BIGINT       NOT NULL COMMENT '소속 handover id',
    `action_id`                 BIGINT       NOT NULL COMMENT '인계 대상 액션 id(C 모듈)',
    `action_title_snap`         VARCHAR(255) NOT NULL COMMENT '액션 제목 스냅샷',
    `action_status_snap`        VARCHAR(255) NOT NULL COMMENT '액션 상태 스냅샷',
    `project_tag_snap`          VARCHAR(255) NULL COMMENT '프로젝트 태그 스냅샷',
    `action_type_snap`          VARCHAR(255) NOT NULL COMMENT '액션 유형 스냅샷(PERSONAL/TEAM)',
    `deadline_snap`             DATE         NULL COMMENT '액션 마감 스냅샷',
    `source_meeting_id`         BIGINT       NULL COMMENT '출처 회의 id(D 모듈)',
    `source_meeting_title_snap` VARCHAR(255) NULL COMMENT '출처 회의 제목 스냅샷',
    `content_snap`              TEXT         NULL COMMENT '액션 내용 스냅샷',
    `reassignee_id`             BIGINT       NULL COMMENT '재분배 대상 member id(null=미분배)',
    `reassignee_name_snap`      VARCHAR(255) NULL COMMENT '재분배 대상 이름 스냅샷',
    `reassignee_position_snap`  VARCHAR(255) NULL COMMENT '재분배 대상 직급 스냅샷',
    `reassigned_at`             DATETIME     NULL COMMENT '재분배 시각',
    PRIMARY KEY (`id`),
    CONSTRAINT `FK_HANDOVER_ITEM_HANDOVER`
        FOREIGN KEY (`handover_id`) REFERENCES `handover` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX `IX_HANDOVER_ITEM_HANDOVER`   ON `handover_item` (`handover_id`);
CREATE INDEX `IX_HANDOVER_ITEM_REASSIGNEE` ON `handover_item` (`reassignee_id`);
