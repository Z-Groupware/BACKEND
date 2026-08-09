-- sub_team → role. 화면에서 이 테이블을 "역할"이라고 부른다.
--
-- 이 시점에 member.role 은 이미 authority 로 비켜났다(V2.3.1). 순서가 뒤집히면
-- member 안에 role 과 role_id 가 동시에 존재하는 혼란기가 생긴다.
--
-- RENAME TABLE 은 이 테이블을 가리키는 FK 참조를 MySQL 이 자동으로 따라 고친다.
RENAME TABLE `sub_team` TO `role`;

-- 제약명은 자동으로 따라오지 않는다 — 테이블명이 바뀐 뒤에도 PK_SUB_TEAM 으로 남는다.
ALTER TABLE `role`
    DROP PRIMARY KEY,
    ADD CONSTRAINT `PK_ROLE` PRIMARY KEY (`id`);

ALTER TABLE `role`
    MODIFY COLUMN `name` VARCHAR(50) NOT NULL
    COMMENT '역할명 (프론트엔드·백엔드·인사). 리더 없음, 인가에 쓰지 않는 라벨';
