package com.module06.backend.identity.team.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

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
 * team 도메인이 보는 역할(구 {@code sub_team}, V2.3.4)의 읽기 전용 참조. member 쪽
 * {@code RoleRefEntity} 와 같은 테이블을 다른 도메인에서 보는 것이라, {@code TeamMemberRefEntity}
 * 와 같은 이유로 도메인마다 자기 참조를 갖는다. 쓰기 금지 — @Immutable 로 dirty checking 자체를 막는다.
 *
 * <p>{@code @GeneratedValue} 를 붙인다 — @Immutable 이라 INSERT 를 타지 않지만, 같은 테이블을
 * 매핑하는 쓰기용 {@code RoleWriteEntity} 와 채번 전략이 갈리면 테스트의 Hibernate
 * {@code create-drop} 스키마 생성이 매핑들을 합칠 때 id 컬럼의 auto-increment 여부가 어긋나
 * 비결정적으로 실패한다(member 쪽 {@code RoleRefEntity} Javadoc 과 같은 이유).
 */
@Entity
@Table(name = "role")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamRoleRefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** NULL 이면 전 회사 공용 시스템 역할이다(V2.3.8). */
    @Column(name = "company_id")
    private Long companyId;

    /** NULL 이면 특정 부서에 매이지 않은 시스템 역할이다(V2.3.9). */
    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "name")
    private String name;
}
