-- =====================================================================
-- V2.2.6 : team 에 팀장 참조(leader_member_id) 추가            [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   "부서마다 팀장 한 명"은 지금 member.role='LEADER' 로만 표현된다.
--   그러면 팀장을 찾을 때마다 member 를 스캔해야 하고, 한 팀에 LEADER 가
--   둘 생겨도 DB 가 막지 못한다. 팀장 교체는 세 가지가 한 트랜잭션으로 도는데
--   (기존 팀장 → MEMBER, 새 팀장 → LEADER, 팀의 팀장 참조 교체)
--   그중 하나만 적용되면 /team/** 게이트가 실제로 뚫린다.
--
--   UNIQUE 제약이 그 2차 방어선이다 — 앱 버그로 두 팀에 같은 사람을
--   팀장으로 앉히려 하면 DB 가 거부한다.
--
-- 설계 메모:
--   · NULL 허용 — 팀장 없는 부서가 정상이다(온보딩 직후, 팀장 퇴사 직후).
--     MySQL 의 UNIQUE 는 NULL 을 중복으로 보지 않으므로 팀장 없는 부서가
--     여러 개 있어도 문제없다.
--   · ON DELETE SET NULL — member 는 소프트 삭제(deleted_at)라 실제 DELETE 가
--     드물지만, 물리 삭제가 일어나도 team 행이 끊긴 id 를 물고 있지 않게 한다.
--   · ADD COLUMN 과 제약 두 개를 한 ALTER 문에 담는다. MySQL 8 의 atomic DDL 은
--     한 문장 단위로 성립하므로, 나누면 컬럼만 생기고 제약이 빠진 상태가
--     남을 수 있다(운영 규칙 8-2).
-- =====================================================================

ALTER TABLE `team`
    ADD COLUMN `leader_member_id` BIGINT NULL
        COMMENT '이 부서의 팀장. NULL 이면 팀장 공석' AFTER `company_id`,
    ADD CONSTRAINT `UK_TEAM_LEADER_MEMBER` UNIQUE (`leader_member_id`),
    ADD CONSTRAINT `FK_TEAM_LEADER_MEMBER` FOREIGN KEY (`leader_member_id`)
        REFERENCES `member` (`id`) ON DELETE SET NULL;
