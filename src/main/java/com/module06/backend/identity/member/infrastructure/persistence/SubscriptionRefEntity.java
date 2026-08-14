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
 * 사이드바의 구독·결제 메뉴 노출 판단용. 읽기 전용이다.
 *
 * <p>{@code status} 도 {@code planCode} 도 enum 으로 매핑하지 않는다 — 이 도메인은 ACTIVE 여부만 보고
 * 결제 상태를 다루지 않는다. 값을 늘리는 변경(과금 담당)이 여기까지 오지 않게 문자열로 둔다.
 *
 * <p>{@code plan} 을 enum 으로 두었다가 실제로 터졌다: 과금 도메인이 같은 행에
 * {@code plan='STANDARD'} 를 쓰기 시작하자, 결제한 회사의 구독 행을 읽는 순간
 * {@code IllegalArgumentException(Unknown name value [STANDARD])} 이 나면서 /me·계정 발급·온보딩이
 * 전부 500 이 되었다. 읽는 쪽이 쓰는 쪽의 값 목록을 복제하고 있었던 것이 원인이라, 복제를 없앤다.
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

    @Column(name = "plan")
    private String planCode;

    @Column(name = "status")
    private String status;
}
