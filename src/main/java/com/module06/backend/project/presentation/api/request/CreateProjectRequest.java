package com.module06.backend.project.presentation.api.request;

/* comment.
    프로젝트 생성 요청 DTO. 담을 값: 이름·태그·기획(description)·색상·마감일·지정 부서 id 목록.
    태그는 영문 전용이라 @Pattern으로 1차 검증한다(중복 검사는 service 책임).
    지정 부서 id는 organization(B)이 제공하는 부서 목록에서 고른 값이다.

    연결된 클래스
    - ProjectController    : 이 DTO를 받는 진입점
    - CreateProjectCommand : 이 DTO가 변환되는 application 명령
*/
public record CreateProjectRequest() {
}
