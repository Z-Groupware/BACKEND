package com.module06.backend.action.domain.model;

/* comment.
    액션 종류. DB action.action_type ENUM과 1:1로 대응한다.
    TEAM은 프로젝트 회의(오너 주최)에서 부서 단위로 분배되며 담당자가 없다.
    PERSONAL은 팀 액션 회의(팀장 주최)에서 팀원 단위로 분배되며 담당자(assignee) 1명을 가진다.

    연결된 클래스
    - Action                     : 이 값을 종류 필드로 보유
    - ActionTypeShapePolicy      : TEAM/PERSONAL별 필드 제약 검증 (domain.policy)
*/
public enum ActionType {
    TEAM,
    PERSONAL
}
