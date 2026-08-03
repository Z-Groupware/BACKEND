package com.module06.backend.project.presentation.api.request;

/* comment.
    프로젝트 수정 요청 DTO. 이름·기획(description)·색상·마감일·상태·지정 부서를 담는다.
    태그 필드는 두지 않는다 — 생성 후 불변(FR-PJ-04)이므로 받을 이유가 없다.

    연결된 클래스
    - ProjectController    : 이 DTO를 받는 진입점
    - UpdateProjectCommand : 이 DTO가 변환되는 application 명령
*/
public record UpdateProjectRequest() {
}
