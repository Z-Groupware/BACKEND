package com.module06.backend.action.presentation.api.response;

/* comment.
    개인 액션 상세 응답 DTO(FR-AC-02). 전 구성원 공개다.
    담당자 이름·부서명·프로젝트 태그까지 조인해서 담고, 체크리스트 목록도 인라인으로 싣는다.
    담을 값: id·title·description·status·dueDate·needsReview·assigneeName·teamName·
    projectTag·parentActionId·checklist 목록.

    연결된 클래스
    - ActionController                          : 이 DTO를 내보내는 진입점
    - ActionService                              : 이 DTO를 만드는 구현체
    - MemberReferenceEntity · TeamReferenceEntity · ProjectReferenceEntity : 조인 대상 (infrastructure.persistence)
    - ChecklistItemResponse                      : 인라인으로 실리는 체크리스트 항목
*/
public record ActionDetailResponse() {
}
