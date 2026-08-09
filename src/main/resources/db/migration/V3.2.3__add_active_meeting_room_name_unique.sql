-- =====================================================================
-- V3.2.3 : 회사별 활성 회의실 이름 유일성 보장 (모성진 레인 V3.2.x)
-- ---------------------------------------------------------------------
-- ROOM-03은 같은 회사 안에서 활성 회의실 이름을 중복 등록할 수 없다는 계약을 가진다.
-- 애플리케이션의 exists 조회는 친절한 조기 검증일 뿐이며, 동시에 들어온 두 트랜잭션이
-- 모두 "없음"을 읽고 INSERT할 수 있으므로 데이터베이스 제약을 최종 동시성 관문으로 둔다.
--
-- 단순 UNIQUE(company_id, name, deleted_at)는 사용할 수 없다. MySQL UNIQUE는 NULL을 서로
-- 다른 값으로 취급하므로 deleted_at이 NULL인 활성 행 두 개를 허용하기 때문이다.
-- 활성 행에서는 이름, 비활성 행에서는 NULL이 되는 생성 컬럼을 만들면 다음 의미가 된다.
--   활성 행   : (company_id, name)이 유일해야 한다.
--   비활성 행 : active_name이 NULL이므로 같은 이름의 이력을 여러 건 보관할 수 있다.
--
-- 애플리케이션은 UK_MEETING_ROOM_ACTIVE_NAME 위반을 MR-002로 변환한다. 따라서 제약 이름을
-- 변경하면 MeetingRoomPersistenceAdapter의 예외 변환 상수도 함께 변경해야 한다.
-- 기존 활성 중복 데이터가 있으면 이 마이그레이션은 의도적으로 실패하므로 먼저 정리해야 한다.
-- 확인 쿼리:
--   SELECT company_id, name, COUNT(*)
--   FROM meeting_room
--   WHERE deleted_at IS NULL
--   GROUP BY company_id, name
--   HAVING COUNT(*) > 1;
-- =====================================================================

ALTER TABLE meeting_room
    ADD COLUMN active_name VARCHAR(100)
        GENERATED ALWAYS AS (
            CASE
                WHEN deleted_at IS NULL THEN name
                ELSE NULL
            END
        ) STORED,
    ADD CONSTRAINT UK_MEETING_ROOM_ACTIVE_NAME
        UNIQUE (company_id, active_name);
