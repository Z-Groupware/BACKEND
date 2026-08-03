package com.module06.backend.project.presentation.api.response;

/* comment.
    프로젝트 목록 행 응답 DTO. 기획(description)은 담지 않는다 — 목록엔 필요 없다.
    담을 값: id·태그·색상·이름·상태·마감일 + 파생값(진행률 progressPct, 완료/전체 액션 수,
    회의 수, 참여 부서 수). 파생값은 DB 컬럼이 아니라 service가 집계한 결과다.

    연결된 클래스
    - ProjectController     : 이 DTO를 내보내는 진입점
    - GetProjectListService : 이 DTO를 만드는 구현체
    - ProjectStatus         : 상태 배지 값
*/
public record ProjectSummaryResponse() {
}
