-- 프론트와 합의한 이름으로 맞춘다. business_number 는 사업자번호·법인번호 어느 쪽인지 모호하다.
--
-- 연락처는 두 개를 유지한다 — manager_phone 은 등록 폼에서 받는 담당자 개인 연락처이고,
-- main_phone 은 기업 설정에서 채우는 회사 대표번호다. 받는 화면도 시점도 달라서 합치면
-- 등록 때 받은 담당자 번호가 회사 대표번호로 노출된다.
--
-- UNIQUE 제약도 이름을 따라간다. 제약명은 RENAME COLUMN 으로 자동으로 바뀌지 않는다.
ALTER TABLE `company`
    DROP INDEX `UK_COMPANY_BUSINESS_NUMBER`;

ALTER TABLE `company`
    RENAME COLUMN `business_number` TO `registration_no`;

ALTER TABLE `company`
    MODIFY COLUMN `registration_no` VARCHAR(20) NULL COMMENT '사업자등록번호 000-00-00000',
    ADD CONSTRAINT `UK_COMPANY_REGISTRATION_NO` UNIQUE (`registration_no`);
