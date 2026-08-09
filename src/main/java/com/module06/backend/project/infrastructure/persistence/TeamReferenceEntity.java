package com.module06.backend.project.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    organization(B) 소유 team 테이블을 읽기 전용으로 조인하기 위한 참조 엔티티.
    쓰기 금지 — @Immutable로 dirty checking 자체를 막는다. id·companyId·name만 조회한다.
*/
@Entity
@Table(name = "team")
@Immutable
@Getter
@NoArgsConstructor
public class TeamReferenceEntity {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "name")
    private String name;
}
