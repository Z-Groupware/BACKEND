package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-07 — 팀 액션 보드 "저장" 버튼의 일괄 상태 변경 기능 계약. All-or-nothing.

    연결된 클래스
    - BulkUpdateTeamActionStatusCommand : 입력
    - TeamActionService : 구현체
    - TeamActionLeaderOnlyPolicy        : 항목별 권한 검사
    - TeamActionController              : 호출자 (presentation)
*/
public interface BulkUpdateTeamActionStatusUseCase {
}
