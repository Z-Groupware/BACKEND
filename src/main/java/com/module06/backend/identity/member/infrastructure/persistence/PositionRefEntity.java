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
 * 화면의 "직급"(팀장·과장·대리·사원) 표시용 읽기 전용 참조. 구 {@code job_position} 이다(V2.3.5).
 *
 * <p>{@code authority} 는 매핑하지 않는다 — 권한 판정은 {@code member.authority} 가 한다.
 * 직급의 기본값은 계정 발급 시점에 한 번 복사될 뿐이고 소급 적용되지 않는다.
 *
 * <p>테이블명을 인용하지 않는다. {@code POSITION} 은 MySQL·H2 양쪽에서 함수 이름이지만 예약어가
 * 아니라 식별자로 그대로 쓸 수 있다. 오히려 인용하면 H2 가 대소문자를 구분하기 시작해서,
 * 인용하지 않은 네이티브 쿼리가 {@code POSITION} 으로 올라가 테이블을 못 찾는다.
 *
 * <p>{@code id} 에 {@code PositionJpaEntity} 와 동일하게 {@code IDENTITY} 생성전략을 붙인다 —
 * 같은 테이블에 매핑되는 두 엔티티의 생성전략이 다르면, 테스트 스키마(Hibernate create-drop)를
 * 만들 때 어느 쪽이 컬럼 정의를 갖게 되는지가 엔티티 처리 순서에 따라 갈려 비결정적으로
 * IDENTITY 가 사라질 수 있다. 이 엔티티는 읽기 전용이라 실제 insert 는 하지 않는다.
 */
@Entity
@Table(name = "position")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionRefEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name")
    private String name;
}
