-- member.authority 와 이름을 맞춘다 — 이 값이 그대로 발급 계정의 authority 가 되기 때문이다.
-- default_role 로 두면 "역할(role 테이블)의 기본값"으로 읽혀 정반대로 이해된다.
--
-- default_ 접두사를 붙이지 않는다. 직급 테이블에 authority 가 하나뿐이라 무엇의 기본값인지
-- 되물을 여지가 없고, member.authority 와 같은 이름이라 "이 값이 그대로 복사된다"가 바로 읽힌다.
--
-- ENUM 값은 건드리지 않는다. LEADER·MEMBER 2값은 V2.2.5 에서 이미 좁혀 놨다.
ALTER TABLE `position`
    RENAME COLUMN `default_role` TO `authority`;

ALTER TABLE `position`
    MODIFY COLUMN `authority` ENUM('LEADER', 'MEMBER') NOT NULL DEFAULT 'MEMBER'
    COMMENT '직급↔권한 매핑. 이 직급으로 발급되는 계정의 member.authority 가 된다 (소급 적용 없음)';
