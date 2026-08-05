-- =====================================================================
-- V2.2.12 : member.status ENUM 축소 (ON_LEAVE 제거)             [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   휴직의 이름을 VACATION 으로 통일했다(V2.2.10·V2.2.11). ON_LEAVE 를 목록에
--   남겨두면 두 값이 공존해 같은 상태가 두 이름으로 저장될 수 있고, 그러면
--   구성원 목록의 상태 필터가 한쪽을 놓친다.
--
-- 선행:
--   V2.2.11 이 ON_LEAVE 행을 VACATION 으로 옮긴다. 그 파일 없이 이걸 먼저 돌리면
--   Error 1265(Data truncated for column 'status')로 실패한다.
--
-- 주의:
--   ENUM 변경은 롤백 불가 DDL 이다. 되돌릴 때는 이 파일을 수정하지 않고 ON_LEAVE 를
--   다시 넣는 새 마이그레이션을 만든다(운영 규칙 3·4).
--
-- 값 목록 (최종):
--   ACTIVE   재직 — 계정 발급 직후도 이 값이다
--   VACATION 휴직 — 휴직 최종 승인 후
--   WAITING  대기 — 휴직·오프보딩을 신청하고 승인을 기다리는 상태
--   RESIGNED 퇴사 — 오프보딩 최종 승인의 감사 흔적. 응답에는 내리지 않는다
--                   (퇴직자는 deleted_at 으로 걸러진다)
--
-- 프론트 영향:
--   shared.ts 의 WorkStatus 가 ON_LEAVE 로 되어 있다(명세 §1-2 근거). VACATION 으로
--   바꿔야 휴직 표시가 맞는다. 명세 §1-2 표도 함께 정정해야 한다.
-- =====================================================================

ALTER TABLE `member`
    MODIFY COLUMN `status` ENUM('ACTIVE', 'VACATION', 'WAITING', 'RESIGNED')
    NOT NULL DEFAULT 'ACTIVE'
    COMMENT 'ACTIVE=재직 / VACATION=휴직 / WAITING=대기 / RESIGNED=퇴사';
