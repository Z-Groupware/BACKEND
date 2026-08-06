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
 * 화면의 "직급"(팀장·과장·대리·사원) 표시용 읽기 전용 참조. 구 {@code job_position} 이다(V2.3.5).
 *
 * <p>{@code authority} 는 매핑하지 않는다 — 권한 판정은 {@code member.authority} 가 한다.
 * 직급의 기본값은 계정 발급 시점에 한 번 복사될 뿐이고 소급 적용되지 않는다.
 *
 * <p>테이블명을 인용하지 않는다. {@code POSITION} 은 MySQL·H2 양쪽에서 함수 이름이지만 예약어가
 * 아니라 식별자로 그대로 쓸 수 있다. 오히려 인용하면 H2 가 대소문자를 구분하기 시작해서,
 * 인용하지 않은 네이티브 쿼리가 {@code POSITION} 으로 올라가 테이블을 못 찾는다.
 */
@Entity
@Table(name = "position")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PositionRefEntity {

    @Id
    private Long id;

    @Column(name = "name")
    private String name;
}
