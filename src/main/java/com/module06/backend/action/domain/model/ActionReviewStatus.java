package com.module06.backend.action.domain.model;

/* comment.
    AI 분배 결과의 검토 상태. needsReview(BOOLEAN)를 대체해 확장한 값이다
    (V2.6.2에서 컬럼 추가, V2.6.3에서 needs_review 드롭 — 결정로그 25번).
    분배 직후에는 항상 PENDING이며, 확정과 반려는 review 도메인 소관이다 —
    C는 값을 저장·노출만 하고 상태를 스스로 올리지 않는다.

    AUTO_CONFIRMED는 2026-08-12 제거됨 — 실사용 코드가 없었고, AI 분배
    정확도 측정은 capture 도메인의 gate_signals.autoConfirmed(boolean)가
    대신 담당한다 (review(A, 이태연) 확인, 이슈 #415).

    연결된 클래스
    - Action                 : 이 값을 갖는 애그리거트 루트
    - ActionDistributionPort : 분배 시 PENDING으로 생성되는 진입점 (application.port)
    - Action.applyHumanReview  : HUMAN_CONFIRMED/REJECTED로 전환하는 도메인 메서드
                                 (review(A)의 ActionReviewApplyPort가 호출, FR-AC-04)
*/
public enum ActionReviewStatus {
    PENDING,
    HUMAN_CONFIRMED,
    REJECTED
}
