-- 갭14/15: handover aggregate 낙관적 락(@Version). 동시 갱신 충돌 시 409.
ALTER TABLE handover ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
