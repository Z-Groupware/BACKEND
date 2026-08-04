package com.module06.backend.action.domain.model;

/* comment.
    개인 액션에 담당자가 붙이는 체크리스트 한 줄(content·완료 여부·정렬 순서).
    담당자 본인만 추가·수정·삭제할 수 있고, 타 구성원은 액션 상세에 포함된 값을 조회만 한다(FR-AC-05).

    연결된 클래스
    - Action                          : 소속 액션 (action_id)
    - ActionChecklistItemRepository   : 저장소 계약
    - ActionChecklistItemJpaEntity    : 영속화 매핑 (infrastructure.persistence)
*/
public class ActionChecklistItem {
}
