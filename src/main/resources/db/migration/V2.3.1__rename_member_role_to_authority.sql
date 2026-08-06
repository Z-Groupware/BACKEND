-- 화면 라벨과 DB 이름을 맞춘다. 화면의 "권한"이 OWNER/LEADER/MEMBER 다.
--
-- role 이라는 이름을 비워주는 것이 이 변경의 진짜 목적이다 — 화면에서 "역할"이라고 부르는 것은
-- 이 컬럼이 아니라 sub_team(V2.3.4 에서 role 로 개명)이다. 이름이 겹친 채로 두면
-- "역할"이 권한인지 하위팀인지 매번 되물어야 한다.
--
-- 순서 주의: 이 파일이 V2.3.2(sub_team_id → role_id)보다 반드시 먼저다.
ALTER TABLE `member`
    RENAME COLUMN `role` TO `authority`;

ALTER TABLE `member`
    MODIFY COLUMN `authority` ENUM('OWNER', 'LEADER', 'MEMBER') NOT NULL DEFAULT 'MEMBER'
    COMMENT '기본 권한. 어드민 겸직은 is_admin 이 담는다';
