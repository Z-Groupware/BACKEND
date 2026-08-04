package com.module06.backend.project.application.usecase;

/* comment.
    FR-PJ-02 — 프로젝트 상세 조회(기획 탭) 기능 계약. 전 구성원 공개다.
    기획(description)도 함께 반환한다 — OWNER 전용이라는 초기안은 팀 협의로 뒤집혔다.
    첨부파일 목록이 이 응답 안에 인라인으로 실린다(FE 기획 탭에서 바로 다운로드 링크 노출).

    연결된 클래스
    - ProjectService : 구현체
    - ProjectDetailResponse   : 출력 DTO (presentation)
    - ProjectController       : 호출자 (presentation)
*/
public interface GetProjectDetailUseCase {
}
