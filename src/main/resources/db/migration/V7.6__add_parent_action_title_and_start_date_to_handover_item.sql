-- V7.6: 인수인계 항목에 상위 팀 액션명·작업 시작일 스냅샷 추가
-- parentActionTitleSnap: 프론트 /app/handover 화면에서 프로젝트 태그 옆 회색 글씨 표시용
-- startDateSnap: 프론트 /team/handover 타임라인 바 렌더링용 (가로 좌표 계산 기준)
ALTER TABLE handover_item
    ADD COLUMN parent_action_title_snap VARCHAR(255) NULL AFTER content_snap,
    ADD COLUMN start_date_snap DATE NULL AFTER parent_action_title_snap;
