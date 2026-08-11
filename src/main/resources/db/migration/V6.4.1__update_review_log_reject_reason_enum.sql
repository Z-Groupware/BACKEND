-- =====================================================================
-- V6.4.1 : review_log.reject_reason ENUM 값 갱신          [담당: 김민섭]
-- ---------------------------------------------------------------------
-- 배경: RVW-02 검토 화면에 제목·내용 인라인 수정, 반려 사유 5종 재정의가
-- 추가되면서(이슈 #350), reject_reason의 허용값을 넓힌다. 이 컬럼은 원래
-- 이태연(A/캡처) 레인(V5.9__create_review_log.sql)이 만들었지만, 담당자
-- 부재중 PM 권한으로 김민섭(action BC)이 진행 — 팀 전체 공지 완료.
--
-- 변경 내용:
--   1. WRONG_TITLE · WRONG_DETAIL 추가 — 제목·내용 인라인 수정 사유(자동 채움).
--   2. NOT_CONFIRMED → NOT_ACTION 으로 의미 확장 개명("논의였는데 확정으로
--      통과됨"에서 "액션으로 분배할 내용이 아님"으로 넓힘). 기존 데이터가
--      있다면 먼저 백필해서 깨진 값이 남지 않게 한다.
--   3. NOT_ATTENDANCE · ETC 신규 반려 사유 추가.
--
-- ALTER 전에 UPDATE로 백필하는 이유: MySQL이 ENUM 정의에서 값을 빼면, 그
-- 값을 쓰던 기존 행은 빈 문자열로 잘리거나(비엄격 모드) ALTER 자체가
-- 실패한다(엄격 모드). 지금 이 회사·환경에 이미 NOT_CONFIRMED로 저장된
-- 행이 있을 수 있으므로 먼저 옮겨 둔다 — 없어도 이 UPDATE는 0행에 영향,
-- 안전하다.
-- =====================================================================

UPDATE `review_log`
    SET `reject_reason` = 'NOT_ACTION'
    WHERE `reject_reason` = 'NOT_CONFIRMED';

ALTER TABLE `review_log`
    MODIFY COLUMN `reject_reason` ENUM(
        'WRONG_ASSIGNEE', 'WRONG_DUE', 'WRONG_TITLE', 'WRONG_DETAIL',
        'HALLUCINATION', 'DUPLICATE', 'NOT_ACTION', 'NOT_ATTENDANCE', 'ETC'
    ) NULL COMMENT 'MODIFY·REJECT에는 필수(422 MEETING_422_3). NOT_CONFIRMED는 V6.4.1로 NOT_ACTION에 흡수됨';
