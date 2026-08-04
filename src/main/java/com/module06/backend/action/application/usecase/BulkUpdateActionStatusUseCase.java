package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-03 — 개인 액션 보드 "저장" 버튼의 일괄 상태 변경 기능 계약. All-or-nothing.

    연결된 클래스
    - BulkUpdateActionStatusCommand    : 입력
    - ActionService : 구현체
    - PersonalActionAssigneeOnlyPolicy : 항목별 권한 검사
    - ActionController                 : 호출자 (presentation)
*/
public interface BulkUpdateActionStatusUseCase {
}
