-- =====================================================================
-- V3.2.1 : 회의실 30분 예약 슬롯 테이블 (모성진 레인 V3.2.x)
-- ---------------------------------------------------------------------
-- 회의가 점유하는 30분 슬롯 한 칸을 한 행으로 물질화한다.
--
-- 왜 이 테이블인가 — "같은 회의실 · 겹치는 시간"은 단일 컬럼 UNIQUE로 표현되지 않고,
-- MySQL 8에는 범위 배제 제약(EXCLUDE)이 없다. 예약 단위가 이미 화면의 30분 그리드이므로
-- 슬롯을 행으로 만들고 (meeting_room_id, slot_start)를 복합 PK로 두면 겹침 판정이
-- 등호 판정으로 축소되고, 중복 예약을 DB가 물리적으로 거부한다.
-- 애플리케이션 락도, 조회 후 판단도 필요 없다.
--   예) 14:00-15:00 예약 = slot_start 14:00 / 14:30 두 행.
--       다른 요청이 같은 두 행을 노리면 둘 중 하나만 커밋된다.
--
-- 사용처 — ROOM-02(예약 현황 조회, 읽기) · MEET-01(회의 개설, 슬롯 INSERT가 동시성 관문).
-- meeting_id FK는 DDL을 파일당 1개로 유지하기 위해 V3.2.2에서 분리해 추가한다.
-- =====================================================================

CREATE TABLE meeting_room_slot
(
    meeting_room_id BIGINT   NOT NULL COMMENT '슬롯을 점유한 회의실 id',
    slot_start      DATETIME NOT NULL COMMENT '30분 그리드 시작 시각',
    meeting_id      BIGINT   NOT NULL COMMENT '슬롯을 점유한 회의 id',
    created_at      DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (meeting_room_id, slot_start)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci;
