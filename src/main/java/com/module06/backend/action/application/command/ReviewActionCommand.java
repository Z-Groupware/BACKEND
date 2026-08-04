package com.module06.backend.action.application.command;

/* comment.
    AI 검토(needsReview) 확인·수정 입력값을 application 계층으로 전달하는 명령 객체(FR-AC-04).
    최초 PATCH 시 담긴 필드를 반영하고 needsReview=false로 확정한다.
    이미 확정된 액션에 재요청이 오면 멱등하게 무시한다(중복 확정 경쟁 방지).

    연결된 클래스
    - ReviewActionRequest   : 이 명령으로 변환되는 요청 DTO (presentation)
    - ReviewActionUseCase   : 이 명령을 받는 기능 계약
    - ActionService : 이 명령을 처리하는 구현체
    - PersonalActionAssigneeOnlyPolicy : 담당자 본인 검사 (application.policy)
*/
public record ReviewActionCommand() {
}
