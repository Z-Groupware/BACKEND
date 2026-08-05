-- =====================================================================
-- V2.2.10 : member.status ENUM 에 VACATION 추가                [담당: 윤종호]
-- ---------------------------------------------------------------------
-- 배경:
--   휴직 상태의 이름이 문서마다 갈려 있었다.
--     DB(V1·V2.2.9)          ON_LEAVE
--     명세 §1-2 열거형 표     ON_LEAVE  (근거: 프론트 shared.ts:26)
--     명세 §2-5 /me 응답      VACATION
--     인수인계 계약           VACATION  (MemberStatusPort#toVacation)
--   응답 필드 이름이 갈리면 프론트의 휴직 표시가 깨지므로 하나로 고정해야 한다.
--   VACATION 으로 통일한다(2026-08-05 확정).
--
-- 3단계로 나누는 이유:
--   MySQL 은 MODIFY COLUMN 에서 ENUM 목록을 바꿀 때 기존 행의 값을 문자열로
--   대조한다. ON_LEAVE 행이 남아 있는 상태에서 목록에서 ON_LEAVE 를 빼면
--   Error 1265(Data truncated)로 실패한다. 반대로 VACATION 이 목록에 없으면
--   UPDATE 도 통하지 않는다. 그래서
--     V2.2.10  목록에 VACATION 을 더한다 (이 파일)
--     V2.2.11  ON_LEAVE 행을 VACATION 으로 옮긴다
--     V2.2.12  목록에서 ON_LEAVE 를 뺀다
--   순서로 나눴다. V2.2.2(정리) → V2.2.3(축소) 와 같은 구조다.
--
--   DDL 은 트랜잭션으로 묶이지 않으므로(운영 규칙 8-2) 한 파일에 담으면 뒤쪽에서
--   실패했을 때 앞쪽이 이미 커밋된 반쪽 상태가 남는다.
--
-- 중간 상태:
--   이 파일만 적용된 시점에는 ON_LEAVE 와 VACATION 이 함께 유효하다. 값을 더하는
--   확장이라 기존 행에 영향이 없고, 두 값이 공존하는 구간은 V2.2.12 에서 닫힌다.
--
-- ⚠️ member 는 공용 테이블이다(운영 규칙 7). 팀 채널에 사전 공유가 필요하다.
--    영향 범위: 인증(/me·로그인) · 구성원 목록 · 휴직/오프보딩(인수인계 도메인)
-- =====================================================================

ALTER TABLE `member`
    MODIFY COLUMN `status` ENUM('ACTIVE', 'ON_LEAVE', 'VACATION', 'WAITING', 'RESIGNED')
    NOT NULL DEFAULT 'ACTIVE'
    COMMENT 'ACTIVE=재직 / VACATION=휴직 / WAITING=대기 / RESIGNED=퇴사 (ON_LEAVE 는 V2.2.12 에서 제거)';
