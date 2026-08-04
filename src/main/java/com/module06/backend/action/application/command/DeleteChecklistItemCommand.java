package com.module06.backend.action.application.command;

/* comment.
    체크리스트 항목 삭제 입력값을 application 계층으로 전달하는 명령 객체(FR-AC-05).
    담을 값: 항목 id. 별도 Request 바디 없이 경로변수만으로 구성되는 삭제 경로다.

    연결된 클래스
    - ChecklistItemUseCase       : 이 명령을 받는 기능 계약
    - ActionChecklistService : 이 명령을 처리하는 구현체
    - PersonalActionAssigneeOnlyPolicy : 담당자 본인 검사 (application.policy)
*/
public record DeleteChecklistItemCommand() {
}
