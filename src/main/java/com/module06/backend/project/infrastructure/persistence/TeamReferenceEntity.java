package com.module06.backend.project.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    organization(B) 소유 team 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    쓰기 금지 — @Immutable로 dirty checking 자체를 막는다. id·companyId·name만 조회한다.

    @Id에 실제 쓰기 엔티티(B의 Team)와 같은 IDENTITY 전략을 명시한다 — 전략이 다르면
    같은 team 테이블에 실제로 insert하는 다른 도메인 테스트가 Hibernate 세션 캐시 충돌로
    깨질 수 있다(윤종호 확인, 2026-08-09 — action 도메인 참조엔티티에서 이미 겪은 문제와 동일).
*/
@Entity
@Table(name = "team")
@Immutable
@Getter
@NoArgsConstructor
public class TeamReferenceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "name")
    private String name;
}
