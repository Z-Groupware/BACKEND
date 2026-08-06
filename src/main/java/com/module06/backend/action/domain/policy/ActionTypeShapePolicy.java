package com.module06.backend.action.domain.policy;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionType;

/* comment.
    TEAM/PERSONAL 액션이 한 테이블을 공유하는 데서 오는 shape 제약 규칙.
    TEAM은 teamId를 갖고 assigneeMemberId를 갖지 않으며, PERSONAL은 assigneeMemberId를 갖는다.
    DB의 action_type CHECK 제약과 동일한 규칙을 애플리케이션 레벨에서 한 번 더 검증한다(이중 방어).
    권한과 무관한 순수 비즈니스 규칙이라 domain.policy에 둔다(권한 판단은 application.policy).
    아직 이 정책을 호출하는 유스케이스(CreateActionService)는 착수 전이라 호출부는 없다 —
    Action 모델과 짝을 이루는 스캐폴딩이라 먼저 채워둔다.

    연결된 클래스
    - Action           : 검사 대상 애그리거트
    - ActionType        : TEAM/PERSONAL 판별 값
*/
public class ActionTypeShapePolicy {

    public void check(ActionType actionType, Long teamId, Long assigneeMemberId) {
        if (actionType == ActionType.TEAM) {
            if (teamId == null) {
                throw new IllegalArgumentException("TEAM 액션은 teamId가 필요합니다.");
            }
            if (assigneeMemberId != null) {
                throw new IllegalArgumentException("TEAM 액션은 담당자를 가질 수 없습니다.");
            }
        } else {
            if (assigneeMemberId == null) {
                throw new IllegalArgumentException("PERSONAL 액션은 담당자가 필요합니다.");
            }
            if (teamId != null) {
                throw new IllegalArgumentException("PERSONAL 액션은 팀을 가질 수 없습니다.");
            }
        }
    }

    public void check(Action action) {
        check(action.getActionType(), action.getTeamId(), action.getAssigneeMemberId());
    }
}
