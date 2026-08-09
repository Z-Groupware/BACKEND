package com.module06.backend.action.domain.model;

/* comment.
    AI 분배 결과의 검토 상태. needsReview(BOOLEAN)를 대체해 4상태로 확장한 값이다
    (V2.6.2에서 컬럼 추가, V2.6.3에서 needs_review 드롭 — 결정로그 25번).
    자동확정(AUTO_CONFIRMED)과 사람확정(HUMAN_CONFIRMED)을 구분해야 AI 분배 정확도를
    측정할 수 있다는 review(A, 이태연) 요구로 나뉘었다.
    분배 직후에는 항상 PENDING이며, 자동확정 판정과 반려는 review 도메인 소관이다 —
    C는 값을 저장·노출만 하고 상태를 스스로 올리지 않는다.

    연결된 클래스
    - Action                 : 이 값을 갖는 애그리거트 루트
    - ActionDistributionPort : 분배 시 PENDING으로 생성되는 진입점 (application.port)
    - Action.applyHumanReview  : HUMAN_CONFIRMED/REJECTED로 전환하는 도메인 메서드
                                 (review(A)의 ActionReviewApplyPort가 호출, FR-AC-04)
*/
public enum ActionReviewStatus {
    PENDING,
    AUTO_CONFIRMED,
    HUMAN_CONFIRMED,
    REJECTED
}
