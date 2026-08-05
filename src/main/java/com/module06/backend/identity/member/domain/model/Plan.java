package com.module06.backend.identity.member.domain.model;

/**
 * subscription.plan 과 값 이름이 정확히 같아야 한다 — @Enumerated(STRING) 으로 매핑한다(V1:254).
 *
 * <p>FREE 를 남겨 둔 것은 DB ENUM 에 아직 그 값이 있기 때문이다. 여기서 지우면 FREE 행을 읽는 순간
 * 매핑이 실패한다. 확정된 과금 모델(정액 + 사용량 초과)에는 무료 등급이 없으므로 결국 없어질 값이지만,
 * 그 정리는 subscription·billing_history 마이그레이션이 먼저이고 과금 담당 영역이다.
 */
public enum Plan {
    FREE, TEAM
}
