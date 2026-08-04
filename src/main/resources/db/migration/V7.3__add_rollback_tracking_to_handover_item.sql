-- 갭1(반려 시 자동 롤백) 준비 필드. 실제 롤백 로직은 C rollbackReassignment 계약 대기.
ALTER TABLE handover_item
    ADD COLUMN committed_at DATETIME NULL,
    ADD COLUMN rollback_status VARCHAR(20) NULL;
