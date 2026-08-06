-- 역할에도 회사 범위를 직접 둔다. team 을 거치지 않고 "이 회사의 역할 전부"를 뽑기 위해서다.
--
-- team_id 로 조인해 가져올 수도 있지만, 역할 목록·중복 검사가 회사 단위로 도는데 그때마다
-- team 을 거치면 조인이 하나씩 더 붙는다. position 테이블도 이미 company_id 를 직접 갖고 있어
-- 두 테이블의 모양을 맞추는 쪽이 일관적이다.
--
-- NOT NULL 로 걸지 않는다 — V2.3.9 가 넣는 시스템 역할(리더·없음)은 특정 회사 소유가 아니다.
-- "회사 역할이면 company_id 가 있고, 시스템 역할이면 NULL" 이 이 컬럼의 의미다.
ALTER TABLE `role`
    ADD COLUMN `company_id` BIGINT NULL
        COMMENT '역할이 속한 기업. NULL 이면 전 회사 공용 시스템 역할' AFTER `id`;

UPDATE `role` r
    JOIN `team` t ON t.`id` = r.`team_id`
    SET r.`company_id` = t.`company_id`;
