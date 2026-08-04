package com.module06.backend.action.application.usecase;

/* comment.
    FR-AC-05 — 체크리스트 항목 추가·수정·삭제 기능 계약. 담당자 본인만 호출할 수 있다.
    타 구성원의 조회는 GetActionDetailUseCase가 상세 응답에 포함해서 내려준다.

    연결된 클래스
    - CreateChecklistItemCommand · UpdateChecklistItemCommand · DeleteChecklistItemCommand : 입력
    - ActionChecklistService : 구현체
    - PersonalActionAssigneeOnlyPolicy  : 권한 검사
    - ActionController                  : 호출자 (presentation)
*/
public interface ChecklistItemUseCase {
}
