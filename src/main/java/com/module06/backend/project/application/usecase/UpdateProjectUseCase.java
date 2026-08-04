package com.module06.backend.project.application.usecase;

/* comment.
    FR-PJ-03 — 프로젝트 수정 기능 계약. OWNER만 호출할 수 있다(조회는 열렸지만 수정은 아니다).
    요청에 태그가 섞여 오더라도 무시한다(FR-PJ-04 태그 불변).
    삭제(D)는 이번 스프린트 스코프 아웃이므로 대응 유스케이스가 없다.

    연결된 클래스
    - UpdateProjectCommand      : 입력
    - ProjectService : 구현체
    - ProjectOwnerOnlyPolicy    : 권한 검사
    - ProjectTagImmutablePolicy : 태그 불변 검사
*/
public interface UpdateProjectUseCase {
}
