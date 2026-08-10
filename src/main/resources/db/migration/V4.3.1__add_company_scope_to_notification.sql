-- =====================================================================
-- V4.3.1 : notification 테이블에 테넌트 스코프·취소 알림 종류·중복 방지 추가 (김현지 레인 V4.x)
-- ---------------------------------------------------------------------
-- notification은 V1 baseline에 이미 있던 테이블이지만(recipient_member_id, type ENUM
-- ('MEETING_CREATED','MEETING_REMINDER'), meeting_id, message, read_at) 실제로 이걸 쓰는
-- 코드가 지금까지 하나도 없었다(신규 착수). 모성진(D)의 MEET-06 회의 취소 알림 연동 요청에서
-- 아래 3가지가 필요해져 이 baseline 테이블을 확장한다:
--
-- 1. company_id 추가 — 테넌트 격리의 물리적 기준. action 테이블의 company_id와 동일하게
--    "의도적 반정규화"다(member/meeting을 조인해서 회사를 알아내지 않고 바로 저장해둔다) —
--    조회할 때마다 회사 조건을 빠뜨릴 위험을 원천 차단한다.
-- 2. type ENUM에 'MEETING_CANCELED' 추가 — 기존 2종류(MEETING_CREATED/MEETING_REMINDER)에
--    이어 회의 취소 알림을 더한다. meeting_id는 이름 그대로 회의 ID로 쓴다(지금 3종류 전부
--    회의 관련 알림이라 억지로 범용화하지 않는다 — 나중에 회의 아닌 알림 종류가 생기면 그때
--    담당자가 새 컬럼 + ENUM 값을 추가한다).
-- 3. UNIQUE(company_id, recipient_member_id, type, meeting_id) 추가 — 실제 중복 알림 방지의
--    최종 방어선(애플리케이션 코드가 아니라 DB가 막는다).
--
-- ⚠️ meeting_id를 NOT NULL로 강제한다(CodeRabbit 지적) — MySQL의 복합 UNIQUE 인덱스는 NULL을
-- "서로 다른 값"으로 취급해서, meeting_id가 NULL이면 같은 (company_id, recipient_member_id,
-- type, NULL) 조합이 몇 번이고 다시 저장돼 위 3번 중복 방지가 무력화된다. 지금 3종류 전부
-- meeting_id가 항상 있는 회의 알림이라 NOT NULL로 막아도 손해가 없다.
--
-- company_id/meeting_id 둘 다 기존 행이 없다는 전제로(이 테이블을 쓰는 코드가 지금까지 없었음)
-- NOT NULL로 바로 추가/변경한다 — 나중에 채워야 할 기존 데이터가 없다.
-- =====================================================================

ALTER TABLE `notification`
    ADD COLUMN `company_id` BIGINT NOT NULL COMMENT '테넌트 스코프용 의도적 반정규화(action 테이블과 동일 패턴)' AFTER `id`,
    MODIFY COLUMN `type` ENUM('MEETING_CREATED', 'MEETING_REMINDER', 'MEETING_CANCELED') NOT NULL,
    MODIFY COLUMN `meeting_id` BIGINT NOT NULL,
    ADD CONSTRAINT `UK_notification_dedup` UNIQUE (`company_id`, `recipient_member_id`, `type`, `meeting_id`),
    ADD CONSTRAINT `FK_notification_company` FOREIGN KEY (`company_id`) REFERENCES `company` (`id`);
