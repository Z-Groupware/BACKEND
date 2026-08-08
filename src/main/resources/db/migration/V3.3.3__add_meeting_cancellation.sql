-- MEET-06 시작 전 회의 취소 상태와 취소 이력 시각을 meeting 테이블에 추가한다.
ALTER TABLE meeting
    MODIFY COLUMN status ENUM ('SCHEDULED', 'IN_PROGRESS', 'DONE', 'CANCELED')
        NOT NULL DEFAULT 'SCHEDULED' COMMENT '회의 상태',
    ADD COLUMN canceled_at DATETIME NULL COMMENT '회의 취소 일시' AFTER ended_at;
