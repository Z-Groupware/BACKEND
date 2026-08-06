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
 * 화면의 "역할"(프론트엔드·백엔드·인사) 표시용 읽기 전용 참조. 구 {@code sub_team} 이다(V2.3.4).
 *
 * <p>조직 계층이 아니라 사원에게 붙는 라벨이라 인가에 관여하지 않는다 — 인가 축은
 * {@link com.module06.backend.identity.member.domain.model.Authority} 다.
 */
@Entity
@Table(name = "role")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleRefEntity {

    @Id
    private Long id;

    @Column(name = "name")
    private String name;
}
