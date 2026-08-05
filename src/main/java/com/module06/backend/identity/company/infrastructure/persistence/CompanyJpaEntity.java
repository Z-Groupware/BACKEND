package com.module06.backend.identity.company.infrastructure.persistence;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * company 테이블을 읽기 위한 엔티티.
 *
 * <p>{@code ddl-auto: validate} 이므로 컬럼 타입·nullable 이 스키마와 정확히 맞아야 앱이 뜬다.
 * 이번 범위에 필요한 컬럼만 매핑한다 — 사업자번호 등은 기업 정보 API 에서 추가한다.
 */
@Entity
@Table(name = "company")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CompanyJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 로그인 1단계 키. 기업 내 유일하며 수정 불가다. */
    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    /** 온보딩 완료 시각. null 이면 미완료 — /me 의 isOnboarded 판정에 쓴다. */
    @Column(name = "onboarded_at")
    private LocalDateTime onboardedAt;
}
