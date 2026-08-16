-- =====================================================================
-- V4.10 : capture_upload_state에 예약 진행 오프셋·완료 블록 수 추가 (김현지 레인 V4.x)
-- ---------------------------------------------------------------------
-- k6 정합성 테스트로 재현된 레이스: triggerIfThresholdReached가 비동기라, 첫 트리거가
-- blocksFormed만 CAS로 선점하고 아직 last_block_end_offset_ms를 못 갱신한(무거운 파이프라인이
-- 도는 중) 사이에 두 번째(지연됐던) 트리거가 같은 구간을 "아직 문턱 안 넘음"으로 오판해
-- 또 예약해버린다 — block_seq가 중복 생성되고 STT도 두 번 제출된다.
--
-- last_block_end_offset_ms(V4.4.1)는 "실제 절단 지점"(조립 경계 기준)이라 무거운 파이프라인이
-- 끝나야만 정해진다. reserved_up_to_offset_ms는 그와 별개로 "예약(자리 선점) 기준 진행
-- 오프셋"이다 — tryReserveNextBlockSeq가 blocksFormed CAS와 같은 트랜잭션 안에서 즉시
-- 전진시켜서, 뒤이은 트리거가 이미 선점된 구간을 다시 문턱 통과로 오판하지 못하게 막는다.
--
-- CodeRabbit 지적(1차 리뷰) — reserved_up_to_offset_ms만으로는 "같은 구간 재예약"만 막지,
-- "앞 블록이 아직 안 끝났는데 다음 블록을 먼저 예약해서 두 블록이 똑같은(옛) 오디오 시작점을
-- 써 서로 겹치는 것"은 못 막는다. finalized_blocks_count(실제로 끝난 블록 수)를 추가해,
-- blocks_formed(예약된 수)와 같을 때만 새 예약을 허용한다 — 세그먼트당 진행 중인 예약을
-- 1개로 제한한다.
-- =====================================================================

ALTER TABLE `capture_upload_state`
    ADD COLUMN `reserved_up_to_offset_ms` INT NOT NULL DEFAULT 0
        COMMENT '블록 예약(자리 선점) 기준 진행 오프셋(ms) — last_block_end_offset_ms(실제 절단 지점)와 별개'
        AFTER `last_block_end_offset_ms`,
    ADD COLUMN `finalized_blocks_count` INT NOT NULL DEFAULT 0
        COMMENT '실제로 끝난(오디오 조립+STT 제출 완료) 블록 수 — blocks_formed와 같아야 진행 중인 예약이 없다는 뜻'
        AFTER `blocks_formed`;

-- 기존 활성 캡처(아직 조립이 안 끝나 이 행이 남아있는, 배포 시점에 녹음 중인 회의)를 백필한다.
-- "지금까지는 밀린(미완료) 예약이 없었다"고 가정하고, blocks_formed까지는 이미 다 끝난 것으로 본다
-- (배포 순간 딱 레이스 중이었던 극히 드문 경우만 예외이며, 그 경우도 다음 트리거가 안전하게
-- 다시 경합할 뿐 데이터가 깨지지는 않는다).
UPDATE `capture_upload_state`
SET `reserved_up_to_offset_ms` = `last_block_end_offset_ms`,
    `finalized_blocks_count` = `blocks_formed`;
