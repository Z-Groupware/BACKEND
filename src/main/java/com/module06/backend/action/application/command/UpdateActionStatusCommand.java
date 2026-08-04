package com.module06.backend.action.application.command;

/* comment.
    개인 액션 단건 상태 변경 입력값을 application 계층으로 전달하는 명령 객체(FR-AC-03).
    담을 값: 액션 id · 변경할 상태값.

    연결된 클래스
    - UpdateActionStatusRequest   : 이 명령으로 변환되는 요청 DTO (presentation)
    - UpdateActionStatusUseCase   : 이 명령을 받는 기능 계약
    - ActionService : 이 명령을 처리하는 구현체
    - PersonalActionAssigneeOnlyPolicy : 담당자 본인 검사 (application.policy)
*/
public record UpdateActionStatusCommand() {
}
