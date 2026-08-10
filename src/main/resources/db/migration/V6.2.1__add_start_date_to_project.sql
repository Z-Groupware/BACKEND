ALTER TABLE `project`
    ADD COLUMN `start_date` DATE NULL
        COMMENT '진행 시작일 — FE 보드 화면 할일/진행중 칸 구분용 표시값. status(raw enum)엔 영향
                 없음, 기존 행은 채울 원천이 없어 NULL 허용(2026-08-10, 이홍근 요청)';
