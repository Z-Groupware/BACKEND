package com.module06.backend.action.domain.model;

/* comment.
    액션 상태 값. 보드 자유 이동 + "저장" 버튼으로 벌크 반영(FR-AC-03, FR-AC-07) — 서버는
    전이 제약을 걸지 않는다.
*/
public enum ActionStatus {
    TODO,
    IN_PROGRESS,
    DONE
}
