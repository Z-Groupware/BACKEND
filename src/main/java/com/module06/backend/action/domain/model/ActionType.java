package com.module06.backend.action.domain.model;

/* comment.
    TEAM/PERSONAL 구분 (FR-AC-06, FR-AC-02). TEAM은 담당자 없이 팀 단위, PERSONAL은
    담당자(assignee) 1명과 상위 TEAM 액션(parentActionId)을 가진다.
*/
public enum ActionType {
    TEAM,
    PERSONAL
}
