-- =====================================================================
-- V2.2.8 : company 에 기업 등록 신청 정보 + 약관 동의 시각 추가  [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   기업 승인 절차를 폐지하면서 신청서가 곧 기업이 된다. 승인 대기 상태가
--   없으니 별도 company_registration 테이블을 만들지 않고 신청 내용을
--   company 에 바로 담는다.
--
-- 약관 동의를 boolean 이 아니라 시각으로 남기는 이유:
--   · 개인정보 처리방침 동의는 수집·이용의 법적 근거다.
--   · 마케팅 수신은 정보통신망법상 별도 동의다(선택 항목).
--   "동의한 적 없다"는 분쟁에서 동의 시각이 유일한 증거이므로 TRUE/FALSE 로는
--   부족하다. 미동의는 NULL 로 둔다 — 동의하지 않았다는 사실 자체가 값이다.
--
-- 설계 메모:
--   · 전부 NULL 허용 — 이미 만들어진 company 행(개발·데모 데이터)에
--     NOT NULL 을 붙이면 ALTER 가 실패한다. 필수 여부는 앱이 강제한다
--     (필수 2종 미동의는 400).
--   · business_number 는 nullable UNIQUE. MySQL 은 NULL 을 중복으로 보지 않아
--     기존 행끼리 충돌하지 않는다. 앱의 "조회 후 INSERT"는 동시 요청에서
--     둘 다 통과하므로 이 제약이 최종 방어선이고, 위반은 409 로 변환한다.
--   · 한 ALTER 문에 담는다 — "신청 정보를 담는다"는 한 논리 변경이고,
--     문장을 나누면 앞의 컬럼만 생긴 채 실패한 상태가 남는다(운영 규칙 8-2).
-- =====================================================================

ALTER TABLE `company`
    ADD COLUMN `business_number`     VARCHAR(20)  NULL COMMENT '사업자등록번호 000-00-00000' AFTER `name`,
    ADD COLUMN `representative_name` VARCHAR(50)  NULL COMMENT '대표자명'                    AFTER `business_number`,
    ADD COLUMN `manager_email`       VARCHAR(255) NULL COMMENT '담당자 이메일 — 오너 계정 ID 가 된다' AFTER `representative_name`,
    ADD COLUMN `manager_phone`       VARCHAR(30)  NULL COMMENT '담당자 연락처'                AFTER `manager_email`,
    ADD COLUMN `address`             VARCHAR(255) NULL COMMENT '주소 — 등록 폼에 없고 기업 설정에서 채운다' AFTER `manager_phone`,
    ADD COLUMN `main_phone`          VARCHAR(30)  NULL COMMENT '대표 전화'                    AFTER `address`,
    ADD COLUMN `employee_scale`      VARCHAR(20)  NULL COMMENT '임직원 규모 구간 (예: 6-20). 선택 항목' AFTER `main_phone`,
    ADD COLUMN `purpose`             VARCHAR(500) NULL COMMENT '이용 목적. 선택 항목'          AFTER `employee_scale`,
    ADD COLUMN `terms_agreed_at`     DATETIME     NULL COMMENT '이용약관 동의 시각 (필수)'     AFTER `purpose`,
    ADD COLUMN `privacy_agreed_at`   DATETIME     NULL COMMENT '개인정보 처리방침 동의 시각 (필수)' AFTER `terms_agreed_at`,
    ADD COLUMN `marketing_agreed_at` DATETIME     NULL COMMENT '마케팅 수신 동의 시각 (선택). NULL 이면 미동의' AFTER `privacy_agreed_at`,
    ADD CONSTRAINT `UK_COMPANY_BUSINESS_NUMBER` UNIQUE (`business_number`);
