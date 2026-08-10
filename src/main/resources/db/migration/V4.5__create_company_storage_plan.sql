-- =====================================================================
-- V4.5 : company_storage_plan — 회사별 저장 용량 한도 (김현지 레인 V4.x)
-- ---------------------------------------------------------------------
-- CompanyTokenPlan(metering, V7.10)과 같은 자리지만 별도 테이블이다 — 토큰은 매달 리셋되는
-- "이번 달 사용량 풀"이고, 스토리지는 리셋 없이 늘었다 줄었다 하는 게이지(청크 업로드로 늘고
-- 조립·삭제로 줆)라 개념 자체가 다르다. 초과 과금(base_fee·overage 단가)도 없다 — 명세
-- (MEETING_403_2 "저장 용량 한도를 초과했습니다")가 요구하는 건 하드 캡뿐이다.
--
-- CAP-01/02(청크 업로드 presign 발급) 시점에 cap이 이 한도를 조회해 새 회의 녹음 시작을 막는다
-- (이미 녹음 중인 회의는 안 끊는다 — 회의 하나가 끝나기 전까지는 계속 허용).
-- =====================================================================

CREATE TABLE `company_storage_plan` (
    `id`                 BIGINT NOT NULL AUTO_INCREMENT,
    `company_id`         BIGINT NOT NULL COMMENT '회사 ID(회사당 1행)',
    `storage_cap_bytes`  BIGINT NOT NULL COMMENT '저장 용량 한도(바이트)',
    PRIMARY KEY (`id`),
    CONSTRAINT `UK_company_storage_plan_company` UNIQUE (`company_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
