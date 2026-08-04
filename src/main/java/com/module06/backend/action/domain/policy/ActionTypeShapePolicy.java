package com.module06.backend.action.domain.policy;

/* comment.
    TEAM/PERSONAL 액션이 한 테이블을 공유하는 데서 오는 shape 제약 규칙.
    TEAM은 teamId를 갖고 assigneeMemberId·parentActionId를 갖지 않으며,
    PERSONAL은 assigneeMemberId·parentActionId를 갖고 teamId를 갖지 않는다.
    DB의 action_type CHECK 제약과 동일한 규칙을 애플리케이션 레벨에서 한 번 더 검증한다(이중 방어).
    권한과 무관한 순수 비즈니스 규칙이라 domain.policy에 둔다(권한 판단은 application.policy).

    연결된 클래스
    - Action                  : 검사 대상 애그리거트
    - ActionType               : TEAM/PERSONAL 판별 값
    - CreateActionService      : 수동 추가 시 이 규칙을 호출하는 유스케이스 (application.service)
*/
public class ActionTypeShapePolicy {
}
