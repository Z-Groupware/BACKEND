package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-02 — 개인 액션 상세 조회 기능 계약. 전 구성원에게 공개된다.
    담당자 이름·부서명·프로젝트 태그까지 조인해서 내려준다.

    연결된 클래스
    - ActionRepository       : 조회
    - MemberReferenceEntity  : 담당자 이름 조인 (infrastructure.persistence)
    - ActionDetailResponse   : 출력 DTO (presentation)
    - ActionController       : 호출자 (presentation)
*/
public interface GetActionDetailUseCase {
}
