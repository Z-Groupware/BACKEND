-- =====================================================================
-- V2.3.12 : team 에 상위 부서 참조(parent_team_id) 추가        [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   API_SPEC.md §6-1~6-3 은 본부→팀 2단계 트리(parentTeamId, children,
--   깊이 제한)를 요구하지만 team 테이블에는 계층 컬럼이 없었다(V1 baseline은
--   id, company_id, name 뿐 — V2.2.6 이 leader_member_id 만 추가했다).
--
-- 설계 메모:
--   · NULL 허용 — NULL 이면 최상위(본부 자신)다.
--   · ON DELETE RESTRICT — 자식이 남은 부모를 지우면 DB 가 거부한다.
--     서비스 레이어의 TEAM_HAS_CHILDREN 검사가 1차 방어, 이 제약이 2차 방어선이다.
--     CASCADE 로 두면 본부 삭제 시 하위 팀이 통째로 사라져 소속 구성원의
--     team_id 가 끊긴 참조로 남는다.
--   · 깊이(최대 2단계) 제한은 자기참조 FK 로 표현할 수 없다 — 애플리케이션
--     검증(TEAM_DEPTH_EXCEEDED)의 몫이다.
-- =====================================================================

ALTER TABLE `team`
    ADD COLUMN `parent_team_id` BIGINT NULL
        COMMENT '상위 부서(본부). NULL 이면 최상위' AFTER `company_id`,
    ADD CONSTRAINT `FK_TEAM_PARENT_TEAM` FOREIGN KEY (`parent_team_id`)
        REFERENCES `team` (`id`) ON DELETE RESTRICT;
