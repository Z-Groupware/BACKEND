-- =====================================================================
-- V2.3.15 : position 이름 유일성을 DB 제약으로 최종 보장        [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   PositionService.create/rename 은 저장 전 existsByCompanyIdAndName 조회로
--   이름 중복을 막지만, 이는 애플리케이션의 조기 검증일 뿐이다. 동시에 들어온
--   두 요청이 모두 "없음"을 읽고 INSERT하면 같은 회사 안에 같은 이름의 직급이
--   두 개 생길 수 있다(TOCTOU). team(V2.3.13, PR #148)에서 이미 겪은 문제와
--   동일해 처음부터 같은 방어를 넣는다.
--
--   position 은 team 과 달리 계층이 없으므로(§6-6~6-9에 parentId 없음) NULL
--   정규화가 필요 없다 — 단순 UNIQUE(company_id, name)로 충분하다.
--
-- 주의:
--   PositionPersistenceAdapter가 UK_POSITION_COMPANY_NAME 위반을
--   POSITION_NAME_DUPLICATED(AU-0xx)로 변환한다. 제약 이름을 바꾸면 그
--   상수도 같이 바꿔야 한다. 기존 중복 데이터가 있으면 이 마이그레이션은
--   의도적으로 실패하므로 먼저 정리해야 한다.
-- =====================================================================

ALTER TABLE `position`
    ADD CONSTRAINT `UK_POSITION_COMPANY_NAME` UNIQUE (`company_id`, `name`);
