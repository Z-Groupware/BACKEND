package com.module06.backend.identity.position.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import com.module06.backend.identity.member.domain.model.Authority;

/**
 * 쓰기 가능한 position 매핑. member 쪽의 읽기 전용 PositionRefEntity 와는 별개다.
 *
 * <p>테이블명을 인용하지 않는다 — {@code PositionRefEntity} 주석 참조: {@code position} 을 백틱으로
 * 감싸면 H2 가 대소문자를 구분하기 시작해 네이티브 조회가 깨진다.
 */
@Entity
@Table(name = "position")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "authority")
    private Authority authority;

    @Column(name = "description")
    private String description;

    private PositionJpaEntity(Long companyId, String name, Authority authority, String description) {
        this.companyId = companyId;
        this.name = name;
        this.authority = authority;
        this.description = description;
    }

    static PositionJpaEntity create(Long companyId, String name, Authority authority, String description) {
        return new PositionJpaEntity(companyId, name, authority, description);
    }

    void update(String name, Authority authority, String description) {
        this.name = name;
        this.authority = authority;
        this.description = description;
    }
}
