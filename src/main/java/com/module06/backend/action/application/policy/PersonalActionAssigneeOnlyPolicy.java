package com.module06.backend.action.application.policy;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.exception.ActionErrorCode;
import com.module06.backend.global.exception.BusinessException;

/* comment.
    FR-AC-03,05 — 개인 액션 상태 변경(벌크)·체크리스트 CRUD는 담당자 본인만 가능하다는 권한 규칙.
    타 MEMBER는 물론 같은 프로젝트 OWNER·LEADER라도 담당자가 아니면 막힌다.
    권한 판단은 인증 주체(JWT claim)를 봐야 하므로 domain이 아닌 application에 둔다.
    Controller의 @PreAuthorize가 1차 차단, 이 정책이 2차 방어(5.2절 방어벽 구분).
    단건 상태변경(PATCH)은 없다 — Figma 확인 결과 개인 액션 상세는 조회 전용이고 상태변경은
    보드(벌크)에서만 일어난다(2026-08-07). AI 검토 확정(FR-AC-04)도 이 정책과 무관하다 —
    review(A)가 RVW-02를 자체 구현했다(ActionService 주석 참고).

    연결된 클래스
    - ActionService          : 개인 액션 벌크 상태 변경 시 항목별 호출
    - ActionErrorCode        : 위반 시 던질 에러 코드 (action.exception)
*/
public class PersonalActionAssigneeOnlyPolicy {

    // 담당자 본인인지 검사 — 아니면 던진다.
    public void check(Action action, Long requesterId) {
        if (!requesterId.equals(action.getAssigneeMemberId())) {
            throw new BusinessException(ActionErrorCode.NOT_ACTION_ASSIGNEE);
        }
    }
}
