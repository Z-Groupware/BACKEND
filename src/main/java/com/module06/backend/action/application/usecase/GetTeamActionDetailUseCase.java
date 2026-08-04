package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-06 — 팀 액션 상세 조회 기능 계약. teamActionId가 전역 고유키라 전 구성원 공개다.
    소속 프로젝트의 첨부파일도 함께 포함해서 내려준다.

    연결된 클래스
    - ActionRepository            : 조회
    - ProjectReferenceEntity      : 소속 프로젝트 첨부파일 조인 (infrastructure.persistence)
    - TeamActionDetailResponse    : 출력 DTO (presentation)
    - TeamActionController        : 호출자 (presentation)
*/
public interface GetTeamActionDetailUseCase {
}
