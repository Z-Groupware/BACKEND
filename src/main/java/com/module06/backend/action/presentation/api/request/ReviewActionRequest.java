package com.module06.backend.action.presentation.api.request;

/* comment.
    AI 검토(needsReview) 확인·수정 요청 DTO(FR-AC-04). 담을 값: 수정할 필드(제목·설명·마감일 등).
    최초 PATCH에서만 반영되고, 이미 확정된 액션에는 멱등하게 무시된다(ReviewActionService 책임).

    연결된 클래스
    - ActionController   : 이 DTO를 받는 진입점
    - ReviewActionCommand : 이 DTO가 변환되는 application 명령
*/
public record ReviewActionRequest() {
}
