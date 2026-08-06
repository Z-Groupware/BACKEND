-- =====================================================================
-- V5.12 : meeting_assignment_tuple — L4 산출 assignment tuple 스테이징
-- ---------------------------------------------------------------------
-- 제품의 핵심 산출물("누가 · 무엇을 · 언제까지")이 처음으로 저장되는 자리다.
--
-- ⚠ 왜 action 에 바로 넣지 않는가
-- 스키마 흔적은 tuple 의 최종 도착지가 action 임을 가리킨다 — action.review_status
-- (V2.6.2, "분배 직후 PENDING") · action.due_date_defaulted(V2.6.4) · review_log.
-- target_type='ACTION'(V5.9)이 전부 그 전제다. 그런데 **지금 넣으면 안 되는 이유가 둘**이다.
--
--   ① 순서. L4 는 파이프라인의 끝이 아니다. L5(관점 다변화 검증) → L6(규칙·모순 검사)
--      → L7(자동확정 게이트 4조건)이 뒤에 있고, action 을 만드는 시점을 정하는 건 L7 이다.
--      L4 직후에 action 을 만들면 검증되지 않은 tuple 이 곧바로 사람의 보드에 꽂힌다.
--      되돌리려면 이미 배정 알림이 나간 액션을 지워야 한다.
--   ② 소유권. action 은 C(김민섭) 도메인 소유이고 상태 전이 메서드가 미정이다.
--      A 가 그 테이블에 직접 INSERT 하면 나중에 전이 규칙이 정해질 때 되돌릴 코드가 된다.
--
-- 그래서 이 테이블은 **A 소유의 대기실**이다. L5·L6·L7 이 붙으면 여기서 판정을 받고,
-- 통과한 행만 C 의 메서드를 통해 action 으로 분배된다. 그때 action_id 가 채워진다.
--
-- action_id 에 FK 를 걸지 않는다. C 소유 테이블을 A 레인 마이그레이션이 참조하면
-- 두 도메인의 배포가 서로 묶인다. 분배 경로가 정해질 때 그쪽 레인에서 건다.
--
-- meeting_decision_id 는 ON DELETE CASCADE 다. tuple 은 "확정된 항목에서 뽑은 것"이므로
-- 근거 항목이 사라지면 tuple 도 근거를 잃는다. ANLZ-01 강제 재실행은 meeting_decision 을
-- 통째로 교체하므로(V5.8 · replace), CASCADE 가 없으면 그 삭제가 FK 로 막혀 재실행 자체가
-- 실패한다. 반대로 여기서 tuple 만 남기면 어느 결정에서 나온 것인지 모르는 배정이 떠돈다.
--   ⚠ action 으로 분배된 뒤(action_id NOT NULL) 재실행하면 이 CASCADE 가 그 이력을 지운다.
--     지금은 분배 경로가 없어 발생하지 않지만, L7 이 붙을 때 "분배된 tuple 은 재실행에서
--     제외" 규칙을 함께 정할 것.
--
-- assignee_candidate_member_id 는 Python 의 assigneeCandidatePersonId 와 같은 값이다.
-- 참석자 명단을 member 에서 만들므로(MeetingParticipantJdbcProvider) personId = member.id 다.
-- 컬럼명을 member_id 로 두는 이유는 이 DB 에 person 이라는 것이 없기 때문이다.
-- NULL 은 "사람을 가리키지만 명단 밖" = unknown_person 이며, 배정 불가를 뜻한다.
--
-- assignee_source 가 NULL 이면 담당자 근거를 모른다는 뜻이다. Python 은 판정 불가를
-- enum 값 'UNKNOWN' 으로 받아 후처리에서 None 으로 바꿔 보낸다(구조화 출력에서
-- nullable+enum 조합이 제공자마다 갈리기 때문). 그 None 이 여기 NULL 로 들어온다.
--
-- due_date 가 NULL 인 경우가 정상이다 — 회의에서 기한을 말하지 않았거나, 기준일
-- (meeting.start_at)을 몰라 상대 표현("다음 주까지")을 계산하지 않은 경우다. 기본값으로
-- 채우지 않는다. 프로젝트 마감일로 채우는 것은 분배 시점의 일이고, 그때 채웠다는 사실을
-- action.due_date_defaulted 가 기록한다 — 여기서 미리 채우면 그 구분이 사라진다.
-- =====================================================================

CREATE TABLE `meeting_assignment_tuple` (
    `id`                           BIGINT       NOT NULL AUTO_INCREMENT,
    `company_id`                   BIGINT       NOT NULL COMMENT '테넌트 스코프. 조회 필터의 첫 조건',
    `meeting_id`                   BIGINT       NOT NULL,
    `meeting_decision_id`          BIGINT       NULL COMMENT '이 tuple 을 뽑은 CONFIRMED 항목. 삭제 시 함께 사라진다',
    `topic_seq`                    INT          NOT NULL DEFAULT 0 COMMENT 'L2 가 나눈 주제 순번',
    `topic`                        VARCHAR(300) NOT NULL COMMENT 'L4 를 주제별로 부르므로 어느 주제에서 나왔는지 남긴다',
    `title`                        VARCHAR(300) NOT NULL COMMENT '무엇을 — action.title 후보',
    `assignee_candidate_member_id` BIGINT       NULL COMMENT '누가. NULL=명단 밖(unknown_person) → 배정 불가',
    `assignee_source`              ENUM('EXPLICIT_CALL', 'FIRST_PERSON') NULL COMMENT '담당자 판정 근거. NULL=판정 불가',
    `due_date`                     DATE         NULL COMMENT '언제까지. NULL 이 정상 — 기본값으로 채우지 않는다',
    `evidence_transcript_id`       BIGINT       NULL COMMENT 'transcript_chunk.id. 근거 강제의 자리',
    `model_name`                   VARCHAR(60)  NULL COMMENT '프롬프트 교체 후 정확도 변화를 무엇 탓으로 돌릴지의 키',
    `prompt_version`               VARCHAR(20)  NULL,
    `action_id`                    BIGINT       NULL COMMENT '분배 결과. NULL=아직 대기실에 있음. FK 는 걸지 않는다(C 소유)',
    `sort_order`                   INT          NOT NULL DEFAULT 0,
    `created_at`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at`                   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    KEY `IX_ASSIGNMENT_TUPLE_COMPANY_MEETING` (`company_id`, `meeting_id`),
    KEY `IX_ASSIGNMENT_TUPLE_DECISION` (`meeting_decision_id`),
    -- 분배 대기 목록 조회용. action_id IS NULL 이 곧 "아직 분배되지 않음"이다.
    KEY `IX_ASSIGNMENT_TUPLE_PENDING` (`meeting_id`, `action_id`),
    CONSTRAINT `FK_ASSIGNMENT_TUPLE_DECISION`
        FOREIGN KEY (`meeting_decision_id`)
        REFERENCES `meeting_decision` (`id`)
        ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
