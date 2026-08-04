package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-03 — 개인 액션 단건 상태 변경 기능 계약. 담당자 본인만 호출할 수 있다.

    연결된 클래스
    - UpdateActionStatusCommand        : 입력
    - ActionService : 구현체
    - PersonalActionAssigneeOnlyPolicy : 권한 검사
    - ActionController                 : 호출자 (presentation)
*/
public interface UpdateActionStatusUseCase {
}
