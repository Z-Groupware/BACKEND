package com.module06.backend.project.application.usecase;

/* comment.
    FR-PJ-02 — 프로젝트 목록 조회 기능 계약. 전 구성원(MEMBER 이상) 공개다.
    응답에는 진행률(progressPct)·회의수·액션수·부서수 같은 파생값이 함께 실린다.
    파생값은 DB 컬럼이 아니므로 이 유스케이스가 집계해서 만든다.

    연결된 클래스
    - GetProjectListService  : 구현체
    - ProjectSummaryResponse : 출력 DTO (presentation)
    - ProjectController      : 호출자 (presentation)
*/
public interface GetProjectListUseCase {
}
