package com.module06.backend.identity.member.application.dto;

/**
 * 구성원 목록 필터(§7-1). {@code LEAVE_PENDING}·{@code OFFBOARDING_PENDING} 은 둘 다
 * {@code workStatus = WAITING} 이지만, handover 테이블을 읽기 전용으로 참조해
 * ({@code HandoverPendingRefEntity}) 어느 쪽 대기인지 가른다 — handover 도메인 코드는 건드리지
 * 않는다(TeamRefEntity·PositionRefEntity와 같은 로컬 참조 컨벤션).
 */
public enum MemberListFilter {
    ALL, LEAVE_PENDING, OFFBOARDING_PENDING
}
