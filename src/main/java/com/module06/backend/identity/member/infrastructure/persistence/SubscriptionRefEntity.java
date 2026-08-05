package com.module06.backend.identity.member.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import com.module06.backend.identity.member.domain.model.Plan;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사이드바의 구독·결제 메뉴 노출 판단용. 읽기 전용이다.
 *
 * <p>{@code status} 를 enum 으로 매핑하지 않는다 — 이 도메인은 ACTIVE 여부만 보고 결제 상태를 다루지
 * 않는다. 값을 늘리는 변경(과금 담당)이 여기까지 오지 않게 문자열로 둔다.
 */
@Entity
@Table(name = "subscription")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubscriptionRefEntity {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "plan")
    private Plan plan;

    @Column(name = "status")
    private String status;
}
