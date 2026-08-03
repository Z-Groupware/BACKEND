package com.module06.backend.project.application.usecase;

/* comment.
    FR-PJ-01 — 프로젝트 생성 기능 계약. OWNER만 호출할 수 있다.
    태그 중복·영문 형식 검증, 지정 부서 연결, 마감일 설정이 이 유스케이스의 책임이다.

    연결된 클래스
    - CreateProjectCommand : 입력
    - CreateProjectService : 구현체
    - ProjectController    : 호출자 (presentation, 미생성)
*/
public interface CreateProjectUseCase {
}
