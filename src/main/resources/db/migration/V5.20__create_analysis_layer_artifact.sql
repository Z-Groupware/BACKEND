-- =====================================================================
-- V5.20 : analysis_layer_artifact — 계층 재개(ANLZ-02)를 위한 산출물 보관
-- ---------------------------------------------------------------------
-- 계층 재개는 "앞 계층을 다시 부르지 않는다"가 전부다(명세 ANLZ-02). 그런데 앞 계층의
-- 산출물이 전부 남아 있는 것은 아니다.
--
--   L3   → meeting_summary · meeting_decision   (V5.7 · V5.8)
--   L3.5 → meeting_decision.gate_status         (V5.8)
--   L4   → meeting_assignment_tuple             (V5.12)
--   L5·L6·L7 → 같은 tuple 행의 컬럼들           (V5.13 · V5.14)
--   L1   → transcript_chunk 의 화자 두 컬럼     (V5.3)
--
-- 남지 않는 것이 둘 있다.
--
--   L1.5 지시어 해소 — 결과를 발화 사본에 주석으로만 붙이고 DB 는 고치지 않는다.
--                      원문을 치환하면 "정말 그렇게 말했나"의 근거가 사라지기 때문이다.
--   L2   주제 분할   — meeting_decision 에 topic_seq·topic 은 남지만 **주제가 어느 발화를
--                      묶은 것인지**(utterance_ids)는 어디에도 남지 않는다. 그 목록이
--                      L3·L3.5·L4·L5 가 모델에 넘기는 문맥 그 자체다.
--
-- 이 둘이 없으면 L4 부터 재개할 때 문맥 없이 모델을 부르게 된다. 빈 문맥으로 부르면 빈
-- 결과에 토큰만 쓰고, 그 빈 결과가 DONE 으로 기록돼 조회는 "분석 완료"라고 말한다 —
-- 이 파이프라인에서 가장 위험한 실패 방향이다. 그래서 그 둘만 따로 보관한다.
--
-- 계층별 전용 테이블을 만들지 않은 이유 — 이건 사람이 보는 데이터가 아니라 파이프라인
-- 내부 중간값이다. 조회 조건이 (meeting_id, layer) 하나뿐이고 필드로 거를 일이 없다.
-- meeting_summary 처럼 화면이 읽는 것이었다면 컬럼으로 폈을 것이다.
--
-- payload 는 JSON 이다. 계층마다 모양이 달라 공통 컬럼으로 펼 수 없고, 펴 봐야 그 컬럼을
-- 읽는 곳이 재개 경로 하나뿐이다.
--
-- 타입은 MySQL JSON 을 쓴다 — 이 저장소의 JSON 컬럼이 전부 그렇다(review_log 셋 ·
-- quality_gold_set · meeting_tuple_vector.payload · handover_insight.payload). 엔티티는
-- String 으로 들고 columnDefinition 만 json 으로 맞추는 것이 같이 붙는 규약이다.
-- TEXT 계열로 두면 Hibernate 의 스키마 검증이 @Lob/CLOB 매핑과 어긋난다.
--
-- 회의당 계층당 1건이다. 재실행하면 새 값으로 갱신한다 — 지난 실행의 문맥을 들고 있으면
-- 이번 실행의 결과와 짝이 맞지 않는다.
-- =====================================================================

CREATE TABLE `analysis_layer_artifact` (
    `id`         BIGINT     NOT NULL AUTO_INCREMENT,
    `meeting_id` BIGINT     NOT NULL,
    `layer`      VARCHAR(8) NOT NULL COMMENT '산출물을 남기는 계층. 지금은 L1.5 · L2 뿐이다',
    `payload`    JSON       NOT NULL COMMENT '계층 산출물 JSON. 재개(ANLZ-02)가 문맥을 되살릴 때만 읽는다',
    `created_at` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    -- 회의당 계층당 1건. analysis_layer 의 UNIQUE(meeting_id, layer)와 같은 규칙이다 —
    -- 두 건이 쌓이면 재개가 어느 쪽을 읽을지 정해야 하고, 그 판단이 조용히 틀리면
    -- 지난 실행의 문맥으로 이번 계층을 부르게 된다.
    UNIQUE KEY `UK_ANALYSIS_LAYER_ARTIFACT_MEETING_LAYER` (`meeting_id`, `layer`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
