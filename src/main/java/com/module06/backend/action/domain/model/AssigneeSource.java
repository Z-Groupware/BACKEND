package com.module06.backend.action.domain.model;

/* comment.
    AI가 액션 담당자를 특정한 근거(V2.6.1). EXPLICIT_CALL=회의에서 이름을 직접 호명당함,
    FIRST_PERSON=본인이 1인칭으로 맡겠다고 발화. 둘 다 아니면 판정 불가라 값이 없다
    (컬럼 NULL 허용) — 담당자를 못 정한 액션은 PERSONAL이 아니라 TEAM + 회의의 team_id로
    떨어진다(결정로그 25번).
    원래 ActionDistributionPort의 중첩 enum이었으나, 도메인 모델(Action)이 이 값을 필드로
    가지면 domain -> application 역방향 의존이 되어 절대규칙 5항을 위반하므로 domain.model로
    옮겼다 — 계약의 필드명·타입명·시그니처는 그대로이고 패키지만 이동했다(2026-08-06).

    연결된 클래스
    - Action                 : 이 값을 갖는 애그리거트 루트
    - ActionDistributionPort : 이 값을 입력으로 받는 계약 (application.port)
*/
public enum AssigneeSource {
    EXPLICIT_CALL,
    FIRST_PERSON
}
