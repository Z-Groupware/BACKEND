package com.module06.backend.identity.member.application.dto;

/**
 * 구성원 목록 필터(§7-1). {@code LEAVE_PENDING}·{@code OFFBOARDING_PENDING} 은 둘 다
 * {@code workStatus = WAITING} 이라 이 값만으로는 구분되지 않는다 — 어느 쪽 대기인지는 인수인계
 * 신청 테이블과 조인해야 하는데, 그 인터페이스는 아직 인수인계 담당과 합의되지 않았다(내 담당 밖).
 * 그래서 지금은 둘 다 WAITING 전체를 돌려준다 — 조인이 붙기 전까지의 임시 동작이다.
 */
public enum MemberListFilter {
    ALL, LEAVE_PENDING, OFFBOARDING_PENDING
}
