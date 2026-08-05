-- =====================================================================
-- V2.2.5 : job_position.default_role ENUM 축소                 [담당: 윤종호]
--        ENUM('OWNER','ADMIN','LEADER','MEMBER') → ENUM('LEADER','MEMBER')
-- ---------------------------------------------------------------------
-- 배경:
--   직급에 줄 수 있는 권한은 LEADER / MEMBER 둘뿐이다(V2.2.4 주석 참조).
--   앱 레이어에서도 400 으로 거부하지만, ENUM 을 남겨두면 DB 는 여전히
--   OWNER·ADMIN 을 받아준다 — 마이그레이션 시드나 수동 UPDATE 로 들어오면
--   그 직급으로 발급되는 계정 전원의 권한이 조용히 어긋난다.
--
-- 선행:
--   V2.2.4 (OWNER/ADMIN 행을 MEMBER 로 정리)
--
-- 주의:
--   ENUM 변경은 롤백 불가 DDL 이다(운영 규칙 8-2).
-- =====================================================================

ALTER TABLE `job_position`
    MODIFY COLUMN `default_role` ENUM('LEADER', 'MEMBER') NOT NULL DEFAULT 'MEMBER'
    COMMENT '직급↔권한 매핑 기본값. 이 직급으로 발급되는 계정의 role 이 된다';
