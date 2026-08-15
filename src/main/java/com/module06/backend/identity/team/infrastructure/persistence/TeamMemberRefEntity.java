package com.module06.backend.identity.team.infrastructure.persistence;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * team 도메인이 보는 member 의 읽기 전용 참조. member 쪽 {@code TeamRefEntity} 의 반대 방향이다.
 * 쓰기 금지 — @Immutable 로 dirty checking 자체를 막는다.
 */
@Entity
@Table(name = "member")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamMemberRefEntity {

    @Id
    private Long id;

    @Column(name = "company_id")
    private Long companyId;

    @Column(name = "team_id")
    private Long teamId;

    /* 역할(구 sub_team). NOT NULL DEFAULT 2(V2.3.10) — 2 는 "없음"이다. */
    @Column(name = "role_id")
    private Long roleId;

    @Column(name = "name")
    private String name;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
