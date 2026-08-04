package com.module06.backend.action.application.command;

/* comment.
    체크리스트 항목 추가 입력값을 application 계층으로 전달하는 명령 객체(FR-AC-05).
    담을 값: 액션 id · 내용(content) · 정렬 순서.

    연결된 클래스
    - CreateChecklistItemRequest : 이 명령으로 변환되는 요청 DTO (presentation)
    - ChecklistItemUseCase       : 이 명령을 받는 기능 계약
    - ActionChecklistService : 이 명령을 처리하는 구현체
    - PersonalActionAssigneeOnlyPolicy : 담당자 본인 검사 (application.policy)
*/
public record CreateChecklistItemCommand() {
}
