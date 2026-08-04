package com.module06.backend.action.presentation.api.request;

/* comment.
    체크리스트 항목 수정 요청 DTO(FR-AC-05). 담을 값: 변경할 내용(content) 또는 완료 여부(isDone).

    연결된 클래스
    - ActionController          : 이 DTO를 받는 진입점
    - UpdateChecklistItemCommand : 이 DTO가 변환되는 application 명령
*/
public record UpdateChecklistItemRequest() {
}
