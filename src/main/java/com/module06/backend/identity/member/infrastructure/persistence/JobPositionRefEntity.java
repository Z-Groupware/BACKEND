package com.module06.backend.identity.member.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 직급명 표시용 읽기 전용 참조. default_role 은 매핑하지 않는다 — 권한 판정은 member.role 이 한다. */
@Entity
@Table(name = "job_position")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class JobPositionRefEntity {

    @Id
    private Long id;

    @Column(name = "name")
    private String name;
}
