-- =====================================================================
-- V2.2.3 : member.role ENUM 축소 (ADMIN 제거)                [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   ADMIN 은 role 값이 아니라 member.is_admin 겸직 플래그다(V2.2.1).
--   ENUM 에 남겨두면 '리더 + 어드민' 같은 조합을 role 로 표현하려는 코드가
--   다시 생기고, 두 표현이 공존해 권한 판정이 갈린다.
--
--   실제 권한 = role(OWNER/LEADER/MEMBER) + is_admin 이면 관리 4종 추가
--
-- 선행:
--   V2.2.2 가 role='ADMIN' 행을 MEMBER 로 정리한다. 그 파일 없이 이걸 먼저 돌리면
--   Error 1265(Data truncated for column 'role')로 실패한다.
--
-- 주의:
--   ENUM 변경은 롤백 불가 DDL 이다. 되돌릴 때는 이 파일을 수정하지 않고
--   ADMIN 을 다시 넣는 새 마이그레이션을 만든다(운영 규칙 3·4).
-- =====================================================================

ALTER TABLE `member`
    MODIFY COLUMN `role` ENUM('OWNER', 'LEADER', 'MEMBER') NOT NULL DEFAULT 'MEMBER'
    COMMENT '기본 역할. 어드민 겸직은 is_admin 이 담는다';
