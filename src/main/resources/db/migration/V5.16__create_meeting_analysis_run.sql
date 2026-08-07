-- =====================================================================
-- V5.16 : meeting_analysis_run — 회의별 분석 실행 번호 (#134 실행 순서 보장)
-- ---------------------------------------------------------------------
-- 같은 회의의 분석이 두 번 들어오면 순서가 보장되지 않는다. 늦게 시작한
-- **오래된** 실행이 나중 실행의 결과를 덮을 수 있다 — 계층 잠금(analysis_layer)은
-- "동시에 같은 계층을 돌리는 것"만 막고, 이미 끝난 계층을 뒤늦게 다시 쓰는 것은
-- 막지 못하기 때문이다.
--
-- 이 테이블이 그 순서의 기준점이다. 실행이 시작될 때 회의의 run_seq 를 1 올려
-- 그 값을 자기 실행 번호로 갖고, 계층 잠금을 잡을 때마다 자기 번호가 아직
-- 최신인지 확인한다. 최신이 아니면 그 실행은 거기서 멈춘다(SUPERSEDED).
--
-- 왜 회의 하나에 한 행인가 — 순서를 정하는 데 필요한 것은 "가장 최근에 시작한
-- 실행이 누구인가" 하나뿐이다. 실행 이력을 쌓으면 회의당 행이 무한히 늘고,
-- 그걸 읽는 쪽은 매번 MAX 를 구해야 한다. 이력이 필요하면 analysis_layer 에
-- 계층별 시각이 이미 있다.
--
-- run_seq 를 회의 스코프로 두는 이유 — 전역 시퀀스면 회의 A 의 실행이 회의 B 의
-- 번호를 올려 서로 무관한 실행끼리 순서를 다투게 된다. 경합의 단위는 회의다.
--
-- company_id 가 없다. analysis_layer 와 같은 이유다 — 회사 스코프는 조회 조건이
-- 아니라 MeetingAccessGuard 라는 관문 하나가 본다(이슈 #100).
-- =====================================================================

CREATE TABLE `meeting_analysis_run` (
    `meeting_id` BIGINT   NOT NULL COMMENT '회의 하나에 한 행. 실행 이력이 아니라 최신 실행 번호만 갖는다',
    `run_seq`    BIGINT   NOT NULL DEFAULT 0 COMMENT '마지막으로 발급된 실행 번호. 실행 시작마다 1씩 오른다',
    `created_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`meeting_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
