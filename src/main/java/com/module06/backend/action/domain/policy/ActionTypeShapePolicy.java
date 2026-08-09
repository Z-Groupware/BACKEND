package com.module06.backend.action.domain.policy;

import com.module06.backend.action.domain.model.ActionType;

/* comment.
    TEAM/PERSONAL 액션이 한 테이블을 공유하는 데서 오는 shape 제약 규칙.
    TEAM은 teamId를 갖고 assigneeMemberId를 갖지 않는다. 애플리케이션 레벨의 유일한 방어선이다 —
    action 테이블에 이 shape을 강제하는 DB CHECK 제약은 없다(예전 클래스 주석이 "DB CHECK와
    동일한 규칙"이라고 적어뒀던 건 사실이 아니었음, 2026-08-07 정정).

    호출 경로가 둘로 갈려 메서드도 나눴다(2026-08-07, 이태연 요청 반영) —
    - checkManual   : 사람이 "+"로 직접 추가(FR-AC-01 예외 경로). PERSONAL은 담당자 필수.
    - checkDistribution : AI 분배(FR-AC-01 정상 경로). PERSONAL이어도 담당자가 없을 수 있다 —
      AI가 참석자 명단 밖을 가리켰거나 이름을 못 찾은 경우이며, RVW-01 검토 화면에서 사람이
      드롭다운으로 채운다. 담당자 없이 확정되지 않게 막는 안전장치는 RVW-05(분배 확정)가
      review 도메인 책임으로 넣는다 — C는 "담당자 없이 PENDING 저장"까지만 허용한다.
    권한과 무관한 순수 비즈니스 규칙이라 domain.policy에 둔다(권한 판단은 application.policy).

    연결된 클래스
    - ActionService              : checkManual 호출
    - ActionDistributionService  : checkDistribution 호출
    - ActionType                 : TEAM/PERSONAL 판별 값
*/
public class ActionTypeShapePolicy {

    public void checkManual(ActionType actionType, Long teamId, Long assigneeMemberId) {
        checkTeamShape(actionType, teamId, assigneeMemberId);
        if (actionType != ActionType.TEAM && assigneeMemberId == null) {
            throw new IllegalArgumentException("PERSONAL 액션은 담당자가 필요합니다.");
        }
    }

    public void checkDistribution(ActionType actionType, Long teamId, Long assigneeMemberId) {
        checkTeamShape(actionType, teamId, assigneeMemberId);
    }

    // 두 경로가 공통으로 지키는 부분 — TEAM은 teamId 필수·담당자 불가, PERSONAL은 팀을 못 가짐.
    private void checkTeamShape(ActionType actionType, Long teamId, Long assigneeMemberId) {
        if (actionType == null) {
            // AI 분배 경로(checkDistribution)는 review(A)가 넘기는 크로스도메인 입력이라 애노테이션
            // 검증이 없다 — null이 안 걸리면 예외 없이 통과해 actionType=null 액션이 조용히 저장된다
            // (2026-08-07, CodeRabbit 지적).
            throw new IllegalArgumentException("actionType은 null일 수 없습니다.");
        }
        if (actionType == ActionType.TEAM) {
            if (teamId == null) {
                throw new IllegalArgumentException("TEAM 액션은 teamId가 필요합니다.");
            }
            if (assigneeMemberId != null) {
                throw new IllegalArgumentException("TEAM 액션은 담당자를 가질 수 없습니다.");
            }
        } else if (teamId != null) {
            throw new IllegalArgumentException("PERSONAL 액션은 팀을 가질 수 없습니다.");
        }
    }
}
