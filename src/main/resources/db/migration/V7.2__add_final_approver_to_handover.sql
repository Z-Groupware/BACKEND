-- 갭12: 최종승인자 감사. 권한 검증은 컨트롤러/B(auth), E는 승인자 스냅샷만 저장.
ALTER TABLE handover
    ADD COLUMN final_approver_id BIGINT NULL,
    ADD COLUMN final_approver_name_snap VARCHAR(255) NULL;
