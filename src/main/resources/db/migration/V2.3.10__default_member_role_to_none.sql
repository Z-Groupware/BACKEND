-- "역할 없음"을 표현하는 방법을 하나로 줄인다.
--
-- V2.3.9 가 id 2 를 '없음' 행으로 만들었으므로, NULL 과 2 가 같은 뜻을 갖는 상태가 됐다.
-- 둘이 공존하면 조회마다 "NULL 도 없음으로 쳐야 하나"를 되묻게 되고, 목록 필터에서
-- role_id = 2 조건이 NULL 행을 빠뜨린다. 그래서 NULL 을 없애고 2 로 모은다.
--
-- 온보딩 전 오너도 2(없음)가 된다 — 실제로 역할이 없으니 맞는 값이다.
-- team_id·position_id 는 그대로 NULL 을 유지한다. 그쪽은 "아직 정해지지 않음"이 실재하는 상태다.
UPDATE `member` SET `role_id` = 2 WHERE `role_id` IS NULL;

ALTER TABLE `member`
    MODIFY COLUMN `role_id` BIGINT NOT NULL DEFAULT 2
    COMMENT '역할(구 sub_team). 2 = 없음. 사원 1명당 1개, 인가에 쓰지 않는 라벨';
