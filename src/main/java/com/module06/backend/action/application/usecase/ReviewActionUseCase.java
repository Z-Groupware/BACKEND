package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-04 — AI 검토(needsReview) 확인·수정 기능 계약. 담당자 본인만 호출할 수 있다.
    이미 확정된 액션에 재요청이 오면 멱등하게 무시한다.

    연결된 클래스
    - ReviewActionCommand              : 입력
    - ActionService : 구현체
    - PersonalActionAssigneeOnlyPolicy : 권한 검사
    - ActionController                 : 호출자 (presentation)
*/
public interface ReviewActionUseCase {
}
