-- =====================================================================
-- V7.11 : company_token_plan 에 입력/출력 방향별 단가 추가 (설명가능 과금)
-- ---------------------------------------------------------------------
-- LLM 은 입력 토큰과 출력 토큰의 원가가 다르다. 총 토큰 하나에 단일 단가를
-- 곱하면 실제 비용 구조가 청구서에 드러나지 않는다 — "왜 이 금액인가"를
-- 사용량으로 설명할 수 없다.
--
-- 기존 token_overage_price_per_1k(총량 기준 초과 단가)는 할당량/예상요금
-- 계산에 그대로 남긴다. 방향별 단가는 "실사용 금액(usage amount)" 계산에만
-- 쓰인다. 두 축을 섞지 않는다.
--
-- 백필: 기존 요금제는 입력·출력 단가를 총량 단가와 같게 시작한다. 그래야
-- 방향 단가를 명시적으로 바꾸기 전까지 금액이 갑자기 튀지 않는다(반올림
-- 단위가 방향별로 나뉘는 차이만 남는데, 그게 바로 이 기능이 드러내려는 것).
-- =====================================================================

ALTER TABLE company_token_plan
    ADD COLUMN input_token_price_per_1k  INT NOT NULL DEFAULT 0 AFTER token_overage_price_per_1k,
    ADD COLUMN output_token_price_per_1k INT NOT NULL DEFAULT 0 AFTER input_token_price_per_1k;

UPDATE company_token_plan
    SET input_token_price_per_1k  = token_overage_price_per_1k,
        output_token_price_per_1k = token_overage_price_per_1k;
