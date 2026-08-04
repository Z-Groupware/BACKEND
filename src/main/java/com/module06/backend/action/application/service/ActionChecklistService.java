package com.module06.backend.action.application.service;

import com.module06.backend.action.application.usecase.ChecklistItemUseCase;

/* comment.
    개인 액션 체크리스트 리소스(FR-AC-05)를 다루는 단일 구현체. 셋 다 쓰기 트랜잭션이다.
    ChecklistItemUseCase 하나가 Create/Update/Delete 3개 커맨드를 받는 구조라 구현체도
    자연히 하나다 — Action 리소스와는 별도 애그리거트(체크리스트 항목)를 다루므로
    ActionService에 합치지 않고 분리했다.

    연결된 클래스
    - ChecklistItemUseCase             : 구현하는 계약
    - CreateChecklistItemCommand · UpdateChecklistItemCommand · DeleteChecklistItemCommand : 입력
    - PersonalActionAssigneeOnlyPolicy : 권한 검사(상위 Action의 담당자 기준)
    - ActionChecklistItemRepository    : 저장
*/
public class ActionChecklistService implements ChecklistItemUseCase {
}
