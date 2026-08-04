package com.module06.backend.action.presentation.api.request;

/* comment.
    체크리스트 항목 추가 요청 DTO(FR-AC-05). 담을 값: 내용(content) · 정렬 순서.

    연결된 클래스
    - ActionController          : 이 DTO를 받는 진입점
    - CreateChecklistItemCommand : 이 DTO가 변환되는 application 명령
*/
public record CreateChecklistItemRequest() {
}
