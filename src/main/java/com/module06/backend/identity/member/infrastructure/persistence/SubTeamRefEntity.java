package com.module06.backend.identity.member.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** /me 의 roleLabel 이 이 이름이다 — sub_team.name 은 "역할 태그"다(V1:225). */
@Entity
@Table(name = "sub_team")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SubTeamRefEntity {

    @Id
    private Long id;

    @Column(name = "name")
    private String name;
}
