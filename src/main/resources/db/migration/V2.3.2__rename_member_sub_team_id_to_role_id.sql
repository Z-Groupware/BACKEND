-- 화면의 "역할"(프론트엔드·백엔드·인사)이 sub_team 이다. 조직 계층이 아니라 사원에게 붙는 라벨이라
-- 인가에 관여하지 않는다 — 그래서 authority(V2.3.1)와 이름이 겹치지 않게 정리한다.
--
-- FK 제약이 없어(V1 확인) 컬럼명만 바꾸면 된다.
ALTER TABLE `member`
    RENAME COLUMN `sub_team_id` TO `role_id`;

ALTER TABLE `member`
    MODIFY COLUMN `role_id` BIGINT NULL
    COMMENT '역할(구 sub_team). 사원 1명당 1개 또는 없음. 인가에 쓰지 않는 라벨';
