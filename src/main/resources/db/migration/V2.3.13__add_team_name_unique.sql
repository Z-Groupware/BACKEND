-- =====================================================================
-- V2.3.13 : team 이름 유일성을 DB 제약으로 최종 보장          [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   TeamService.create/rename 는 저장 전 existsByCompanyIdAndParentTeamId...
--   조회로 이름 중복을 막지만, 이는 애플리케이션의 조기 검증일 뿐이다.
--   동시에 들어온 두 요청이 모두 "없음"을 읽고 INSERT하면 같은 부모 아래
--   같은 이름의 부서가 두 개 생길 수 있다(TOCTOU). CodeRabbit PR #139 지적.
--
-- 설계 메모:
--   · 단순 UNIQUE(company_id, parent_team_id, name)은 쓸 수 없다. MySQL의
--     UNIQUE는 NULL을 서로 다른 값으로 취급하므로, parent_team_id가 모두
--     NULL인 최상위 부서들끼리는 이름이 겹쳐도 제약이 걸리지 않는다.
--     (동일 사고: V3.2.3__add_active_meeting_room_name_unique.sql)
--   · NULL을 0으로 정규화하는 생성 컬럼을 두면 다음 의미가 된다.
--       하위 부서 : (company_id, parent_team_id, name) 유일
--       최상위 부서 : (company_id, 0, name) 유일 — 즉 회사 안에서 유일
--     0은 team.id(AUTO_INCREMENT, 1부터 시작)와 절대 겹치지 않는 안전한
--     sentinel이다.
--   · TeamPersistenceAdapter가 UK_TEAM_COMPANY_PARENT_NAME 위반을
--     TEAM_NAME_DUPLICATED(AU-016)로 변환한다. 제약 이름을 바꾸면 그
--     상수도 같이 바꿔야 한다.
--   · 기존 중복 데이터가 있으면 이 마이그레이션은 의도적으로 실패한다.
--     먼저 정리해야 한다. 확인 쿼리:
--       SELECT company_id, COALESCE(parent_team_id, 0) AS scope, name, COUNT(*)
--       FROM team
--       GROUP BY company_id, scope, name
--       HAVING COUNT(*) > 1;
-- =====================================================================

ALTER TABLE `team`
    ADD COLUMN `name_scope_parent_id` BIGINT
        GENERATED ALWAYS AS (COALESCE(`parent_team_id`, 0)) STORED
        COMMENT '이름 유일성 스코프 정규화 — parent_team_id NULL을 0으로 치환',
    ADD CONSTRAINT `UK_TEAM_COMPANY_PARENT_NAME`
        UNIQUE (`company_id`, `name_scope_parent_id`, `name`);
