-- =====================================================================
-- V2.3.19 : 부서별 활성 팀장 유일성 보장                        [담당: 윤종호]
-- ---------------------------------------------------------------------
-- "부서마다 팀장은 최대 한 명"을 데이터베이스가 세게 한다. 지금까지 이 규칙은
-- team.leader_member_id 컬럼이 하나라는 사실로만 보장됐고, member.authority
-- 쪽은 아무도 세지 않아 두 표현이 어긋날 수 있었다(V2.3.18 주석 참조).
--
-- 애플리케이션의 사전 검사(MemberIssuer#persist)는 친절한 조기 거절일 뿐이다.
-- 같은 부서로 동시에 들어온 두 발급 요청이 모두 "팀장 공석"을 읽고 INSERT 할 수
-- 있으므로, 최종 동시성 관문은 데이터베이스여야 한다.
--
-- 단순 UNIQUE(team_id, authority)는 쓸 수 없다 — 한 부서의 평구성원이 한 명으로
-- 제한돼 버린다. 활성 팀장 행에서만 team_id 가 되고 나머지에서는 NULL 이 되는
-- 생성 컬럼을 두면 다음 의미가 된다(V3.2.3 회의실 이름 유일성과 같은 기법).
--   활성 팀장 행 : team_id 가 유일해야 한다 → 부서당 한 명.
--   그 밖의 행   : NULL 이라 MySQL UNIQUE 가 서로 다른 값으로 취급해 제한 없음.
--
-- 퇴사자(deleted_at)가 자동으로 빠지는 것이 이 정의의 핵심이다. 퇴사한 팀장이
-- 계속 자리를 차지하면 후임 승급이 영원히 막힌다.
--
-- 부서 없는 구성원(team_id IS NULL)은 생성 컬럼도 NULL 이라 제약 밖이다.
--
-- 애플리케이션은 UK_MEMBER_ACTIVE_TEAM_LEADER 위반을
-- MEMBER_TEAM_LEADER_ALREADY_EXISTS 로 변환한다(MemberDirectoryCommandAdapter).
-- 따라서 제약 이름을 바꾸면 그 상수도 함께 바꿔야 한다.
-- =====================================================================

ALTER TABLE `member`
    ADD COLUMN `active_leader_team_id` BIGINT
        GENERATED ALWAYS AS (
            CASE
                WHEN `authority` = 'LEADER' AND `deleted_at` IS NULL THEN `team_id`
                ELSE NULL
            END
        ) STORED
        COMMENT '활성 팀장일 때만 team_id. 부서당 팀장 한 명을 UNIQUE 로 세기 위한 생성 컬럼',
    ADD CONSTRAINT `UK_MEMBER_ACTIVE_TEAM_LEADER` UNIQUE (`active_leader_team_id`);
