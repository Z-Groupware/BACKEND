package com.module06.backend.identity.member.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 구성원 목록의 WAITING 세분화(§7-1: LEAVE_PENDING vs OFFBOARDING_PENDING)용 읽기 전용 참조.
 * handover 도메인의 {@code handover} 테이블을 이 패키지 안에서만 읽는다 — TeamRefEntity·
 * PositionRefEntity와 같은 컨벤션이다(다른 도메인 테이블을 로컬 @Immutable 참조로 읽고, 그
 * 도메인의 실제 엔티티·서비스는 참조하지 않는다). 쓰기 금지.
 */
@Entity
@Table(name = "handover")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HandoverPendingRefEntity {

    @Id
    private Long id;

    @Column(name = "writer_member_id")
    private Long writerMemberId;

    /** VACATION | OFFBOARDING. handover 도메인의 HandoverType과 값이 같다. */
    @Column(name = "handover_type")
    private String handoverType;

    /** SUBMITTED | REASSIGNED | FINALIZED | REJECTED. FINALIZED·REJECTED 는 더 이상 대기가 아니다. */
    @Column(name = "status")
    private String status;
}
