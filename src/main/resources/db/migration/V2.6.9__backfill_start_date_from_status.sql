-- V2.6.7이 start_date를 NULL만 넣고 기존 행을 백필하지 않았다. start_date가 비어 있으면
-- 새 파생 로직이 무조건 TODO로 읽으므로, 안 채우면 진행 중이던 액션이 "할일"로 되돌아간
-- 것처럼 보인다(2026-08-07, CodeRabbit 지적).
--
-- 실제 시작일은 남아있지 않아 복원 불가 — updated_at(마지막 변경 시각)을 최선의 근사값으로
-- 쓴다. 완전한 정확도는 아니지만, "언젠가 시작함"이라는 사실 자체를 잃는 것보다는 낫다.
--
-- 이미 push된 V2.6.7을 직접 고치지 않고 새 파일로 보완한다(migration 수정 금지 원칙).
UPDATE `action` SET `start_date` = DATE(`updated_at`) WHERE `status` = 'IN_PROGRESS';
