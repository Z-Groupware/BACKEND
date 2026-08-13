-- personal_todo에 기간 종료일 추가 (김민섭 레인 V6.x, personal_todo 전용 테이블이라
-- 공용테이블 사전공지 대상 아님). 기존 행 백필은 V6.5.2, NOT NULL 전환은 V6.5.3에서
-- 별도 파일로 진행한다(ALTER 하나 = 파일 하나 원칙, V2.6.7~V2.6.9 선례 따름).
ALTER TABLE `personal_todo`
    ADD COLUMN `end_date` DATE NULL COMMENT '기간 종료일. 단일 날짜 Todo는 date와 동일값' AFTER `date`;
