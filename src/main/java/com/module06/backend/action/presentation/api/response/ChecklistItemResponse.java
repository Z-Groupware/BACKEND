package com.module06.backend.action.presentation.api.response;

/* comment.
    체크리스트 항목 응답 DTO(FR-AC-05). 액션 상세 응답에 인라인으로 실린다.
    담을 값: id·content·isDone·sortOrder.

    연결된 클래스
    - ActionController      : 이 DTO를 내보내는 진입점
    - ActionChecklistService : 이 DTO를 만드는 구현체
    - ActionDetailResponse   : 이 DTO를 목록으로 담는 상위 응답
*/
public record ChecklistItemResponse() {
}
