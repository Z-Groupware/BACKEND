package com.module06.backend.identity.member.infrastructure.persistence;

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
 * 화면의 "역할"(프론트엔드·백엔드·인사) 표시용 읽기 전용 참조. 구 {@code sub_team} 이다(V2.3.4).
 *
 * <p>조직 계층이 아니라 사원에게 붙는 라벨이라 인가에 관여하지 않는다 — 인가 축은
 * {@link com.module06.backend.identity.member.domain.model.Authority} 다.
 *
 * <p>{@code @GeneratedValue}를 붙인다 — {@code @Immutable}이라 이 엔티티가 INSERT를 타는 일은
 * 없지만, 같은 테이블을 매핑하는 쓰기용 {@link RoleWriteEntity}와 채번 전략이 갈리면 테스트의
 * Hibernate {@code create-drop} 스키마 생성이 두 매핑을 합칠 때 id 컬럼의 auto-increment 여부가
 * 어긋나 비결정적으로 실패한다(다른 Ref 엔티티에서 이미 겪은 문제와 같은 원인).
 */
@Entity
@Table(name = "role")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleRefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;
}
