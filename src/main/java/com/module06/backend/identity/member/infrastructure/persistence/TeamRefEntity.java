package com.module06.backend.identity.member.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 팀명 표시용 읽기 전용 참조. 쓰기 금지 — @Immutable 로 dirty checking 자체를 막는다. */
@Entity
@Table(name = "team")
@Immutable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TeamRefEntity {

    @Id
    private Long id;

    @Column(name = "name")
    private String name;
}
