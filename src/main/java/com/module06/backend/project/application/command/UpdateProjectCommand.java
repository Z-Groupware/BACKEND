package com.module06.backend.project.application.command;

/* comment.
    프로젝트 수정 입력값 명령 객체. 이름·기획(description)·색상·마감일·상태·지정 부서를 담는다.
    태그는 담지 않는다 — 생성 후 불변(FR-PJ-04)이라 애초에 수정 경로에 태울 필요가 없다.

    연결된 클래스
    - UpdateProjectRequest       : 이 명령으로 변환되는 요청 DTO (presentation)
    - UpdateProjectUseCase       : 이 명령을 받는 기능 계약
    - UpdateProjectService       : 이 명령을 처리하는 구현체
    - ProjectTagImmutablePolicy  : 태그 제외 근거가 되는 도메인 규칙
*/
public record UpdateProjectCommand() {
}
