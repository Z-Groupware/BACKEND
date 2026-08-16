-- =====================================================================
-- V4.10 : capture_upload_state에 예약 진행 오프셋 추가 (김현지 레인 V4.x)
-- ---------------------------------------------------------------------
-- k6 정합성 테스트로 재현된 레이스: triggerIfThresholdReached가 비동기라, 첫 트리거가
-- blocksFormed만 CAS로 선점하고 아직 last_block_end_offset_ms를 못 갱긴(무거운 파이프라인이
-- 도는 중) 사이에 두 번째(지연됐던) 트리거가 같은 구간을 "아직 문턱 안 넘음"으로 오판해
-- 또 예약해버린다 — block_seq가 중복 생성되고 STT도 두 번 제출된다.
--
-- last_block_end_offset_ms(V4.4.1)는 "실제 절단 지점"(조립 경계 기준)이라 무거운 파이프라인이
-- 끝나야만 정해진다. 이 컬럼은 그와 별개로 "예약(자리 선점) 기준 진행 오프셋"이다 —
-- tryReserveNextBlockSeq가 blocksFormed CAS와 같은 트랜잭션 안에서 즉시 전진시켜서, 뒤이은
-- 트리거가 이미 선점된 구간을 다시 문턱 통과로 오판하지 못하게 막는다.
-- =====================================================================

ALTER TABLE `capture_upload_state`
    ADD COLUMN `reserved_up_to_offset_ms` INT NOT NULL DEFAULT 0
        COMMENT '블록 예약(자리 선점) 기준 진행 오프셋(ms) — last_block_end_offset_ms(실제 절단 지점)와 별개'
        AFTER `last_block_end_offset_ms`;
