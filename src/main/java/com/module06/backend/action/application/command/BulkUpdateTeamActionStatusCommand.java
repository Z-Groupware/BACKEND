package com.module06.backend.action.application.command;

/* comment.
    팀 액션 보드 "저장" 버튼 클릭 시 일괄 상태 변경 입력값을 전달하는 명령 객체(FR-AC-07).
    담을 값: (팀 액션 id, 변경할 상태값) 목록. All-or-nothing으로 처리한다.

    연결된 클래스
    - BulkUpdateActionStatusRequest    : 이 명령으로 변환되는 요청 DTO(개인·팀 공용, presentation)
    - BulkUpdateTeamActionStatusUseCase: 이 명령을 받는 기능 계약
    - TeamActionService : 이 명령을 처리하는 구현체
    - TeamActionLeaderOnlyPolicy       : 항목별 해당 팀 LEADER 검사 (application.policy)
*/
public record BulkUpdateTeamActionStatusCommand() {
}
