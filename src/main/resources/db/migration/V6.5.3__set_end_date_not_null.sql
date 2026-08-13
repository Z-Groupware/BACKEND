-- V6.5.2 백필 완료 후 NOT NULL로 전환. 이 시점 이후 생성되는 모든 행은 애플리케이션
-- 계층(PersonalTodoService)에서 end_date를 항상 채워 넣는다(미지정 시 date와 동일값).
ALTER TABLE `personal_todo`
    MODIFY COLUMN `end_date` DATE NOT NULL COMMENT '기간 종료일. 단일 날짜 Todo는 date와 동일값';
