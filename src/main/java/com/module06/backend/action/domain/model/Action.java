package com.module06.backend.action.domain.model;

/* comment.
    액션 애그리거트 루트. TEAM/PERSONAL 두 종류를 한 테이블·한 모델로 표현하는 자기참조 구조다.
    TEAM은 담당자 없이 팀 단위로 존재하며 한 팀에 여러 개 동시에 있을 수 있다.
    PERSONAL은 담당자(assignee) 1명과 상위 TEAM 액션(parentActionId)을 가진다.
    사용자는 액션을 직접 만들지 않는다 — A도메인이 ActionDistributionPort로 생성하며,
    AI가 놓친 액션만 "+" 버튼으로 사람이 예외적으로 수동 추가한다(FR-AC-01).
    needsReview는 담당자의 최초 PATCH로 확정되며, 이후 재요청은 멱등하게 무시한다(FR-AC-04).
    지정 부서·담당자·프로젝트는 다른 도메인 엔티티를 참조하지 않고 id 값만 가진다(0절 절대규칙 1항).

    연결된 클래스
    - ActionType               : TEAM/PERSONAL 구분 (FR-AC-06, FR-AC-02)
    - ActionStatus              : 상태 값(TODO/IN_PROGRESS/DONE)
    - ActionChecklistItem      : 이 액션에 딸린 체크리스트 항목(FR-AC-05)
    - ActionTypeShapePolicy    : TEAM↔PERSONAL 필드 제약 규칙
    - ActionRepository         : 저장소 계약
    - ActionJpaEntity          : 영속화 매핑 (infrastructure.persistence)
*/
public class Action {
}
