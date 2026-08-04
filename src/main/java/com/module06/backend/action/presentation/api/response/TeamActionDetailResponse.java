package com.module06.backend.action.presentation.api.response;

/* comment.
    팀 액션 상세 응답 DTO(FR-AC-06). teamActionId가 전역 고유키라 전 구성원 공개다.
    소속 프로젝트의 첨부파일 목록을 인라인으로 함께 담는다(FE가 별도 호출 없이 렌더링).
    담을 값: id·title·description·status·dueDate·teamName·projectTag·첨부파일 목록.

    첨부파일 항목의 필드 구성은 project 도메인 AttachmentResponse와 같은 shape을 따르되,
    도메인 간 presentation DTO를 직접 참조하지 않는다(0절 1항) — action이 자체 타입으로 복제해서 쓴다.

    연결된 클래스
    - TeamActionController   : 이 DTO를 내보내는 진입점
    - TeamActionService       : 이 DTO를 만드는 구현체
    - ProjectReferenceEntity  : 소속 프로젝트 첨부파일 조인 (infrastructure.persistence)
*/
public record TeamActionDetailResponse() {
}
