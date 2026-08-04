package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-07 — 팀 액션 단건 상태 변경 기능 계약. 해당 팀 LEADER만 호출할 수 있다.

    연결된 클래스
    - UpdateTeamActionStatusCommand : 입력
    - TeamActionService : 구현체
    - TeamActionLeaderOnlyPolicy    : 권한 검사
    - TeamActionController          : 호출자 (presentation)
*/
public interface UpdateTeamActionStatusUseCase {
}
