-- =====================================================================
-- V5.3 : transcript_chunk 확장 — 화자 · 종료 오프셋 · 블록 참조
-- ---------------------------------------------------------------------
-- ⚠ 공용 테이블 변경이다. 머지 전 팀 채널 사전 공유 필요(규칙 §7).
--
--   [DB 변경 예정]
--   파일명: V5.3__extend_transcript_chunk_with_speaker.sql
--   대상: transcript_chunk
--   변경 내용: speaker_member_id · speaker_source · end_offset_ms · stt_block_seq 추가
--   영향 범위: 회의 요약 · 발화 기록 조회(ANLZ-05) · 액션 근거 표시
--
-- transcript_chunk 를 정본으로 확정한 데 따른 복원이다. 초기 데이터 사전의
-- transcript_segment(speaker_id, start_ms, end_ms, content) 가 baseline으로
-- 옮겨지면서 화자와 종료시각이 빠졌다. end_offset_ms 가 없으면 자막과 시간창을
-- 겹쳐볼 수 없어 화자 이식(L1)이 아예 동작하지 않는다.
--
-- 기존 offset_ms 는 시작 오프셋으로 그대로 쓴다(= API의 startOffsetMs).
-- start_offset_ms 를 새로 만들면 의미가 겹치고, 공용 테이블 rename 은 다른
-- 도메인 코드를 깨뜨린다. 주석만 명확히 해두고 API 표면에서 매핑한다.
--
-- speaker_member_id 는 NULL 을 허용한다. 판정 포기는 오류가 아니라 정상 동작이다
-- (자막 미사용 참석자, 1·2등 rms 차이 3dB 미만). 틀린 이름을 채우는 것보다 낫다.
--
-- DDL 한 문장으로 묶는다 — MySQL은 여러 DDL을 트랜잭션으로 되돌릴 수 없다(규칙 §8-2).
-- =====================================================================

ALTER TABLE `transcript_chunk`
    ADD COLUMN `speaker_member_id` BIGINT NULL COMMENT 'L1 화자 귀속 결과. NULL=판정 포기(정상). 프론트에서 에러 처리 금지',
    ADD COLUMN `speaker_source`    ENUM('SELF_STREAM', 'ELIMINATION') NULL COMMENT '판정 근거. SELF_STREAM=본인 스트림 rms 최대 / ELIMINATION=소거법 / NULL=미판정',
    ADD COLUMN `end_offset_ms`     INT NULL COMMENT '발화 종료 오프셋. 자막과의 ±1.5초 시간창 매칭에 필수',
    ADD COLUMN `stt_block_seq`     INT NULL COMMENT '이 발화를 만든 stt_block.block_seq. 블록 재처리 시 교체 범위',
    MODIFY COLUMN `offset_ms`      INT NULL COMMENT '발화 시작 오프셋. CAP-01의 startedAtEpochMs 기준 경과 ms (= API의 startOffsetMs)',
    ADD KEY `IX_TRANSCRIPT_CHUNK_MEETING_OFFSET` (`meeting_id`, `offset_ms`),
    ADD KEY `IX_TRANSCRIPT_CHUNK_MEETING_BLOCK` (`meeting_id`, `stt_block_seq`);
