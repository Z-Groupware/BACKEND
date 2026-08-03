package com.module06.backend.project.application.command;

/* comment.
    프로젝트 생성 입력값을 application 계층으로 전달하는 명령 객체.
    담을 값: 이름·태그·기획(description)·색상·마감일·지정 부서 id 목록.
    Controller의 Request DTO를 그대로 내려보내지 않기 위한 경계 역할이다.

    연결된 클래스
    - CreateProjectRequest : 이 명령으로 변환되는 요청 DTO (presentation, 미생성)
    - CreateProjectUseCase : 이 명령을 받는 기능 계약
    - CreateProjectService : 이 명령을 처리하는 구현체
*/
public record CreateProjectCommand() {
}
