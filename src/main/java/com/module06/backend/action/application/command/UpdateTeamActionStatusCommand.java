package com.module06.backend.action.application.command;

/* comment.
    팀 액션 단건 상태 변경 입력값을 application 계층으로 전달하는 명령 객체(FR-AC-07).
    담을 값: 팀 액션 id · 변경할 상태값.

    연결된 클래스
    - UpdateTeamActionStatusUseCase : 이 명령을 받는 기능 계약
    - TeamActionService : 이 명령을 처리하는 구현체
    - TeamActionLeaderOnlyPolicy    : 해당 팀 LEADER 검사 (application.policy)
*/
public record UpdateTeamActionStatusCommand() {
}
