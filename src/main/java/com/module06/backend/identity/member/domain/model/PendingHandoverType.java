package com.module06.backend.identity.member.domain.model;

/**
 * WAITING 상태 구성원의 대기 종류(§7-1) — 휴직 승인 대기인지 오프보딩 승인 대기인지 구분한다.
 * {@code handover.handover_type} 값과 이름이 같아야 한다 — 문자열로 매핑하되 handover 도메인
 * 클래스는 참조하지 않는다({@link com.module06.backend.identity.member.infrastructure.persistence.HandoverPendingRefEntity}
 * 참고 — 도메인 경계를 넘지 않도록 읽기 전용 참조 엔티티로 읽는다).
 */
public enum PendingHandoverType {
    VACATION, OFFBOARDING
}
