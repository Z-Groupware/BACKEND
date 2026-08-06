-- 화면의 "직급"(팀장·과장·대리·사원)이 job_position 이다.
-- FK 제약이 없어(V1 확인) 컬럼명만 바꾼다.
ALTER TABLE `member`
    RENAME COLUMN `job_position_id` TO `position_id`;

ALTER TABLE `member`
    MODIFY COLUMN `position_id` BIGINT NULL
    COMMENT '직급(구 job_position). 발급 시 이 직급의 default_authority 가 member.authority 가 된다';
