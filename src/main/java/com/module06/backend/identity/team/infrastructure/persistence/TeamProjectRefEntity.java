package com.module06.backend.identity.team.infrastructure.persistence;

import org.hibernate.annotations.Immutable;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** team 도메인이 보는 project_team 의 읽기 전용 참조 — 삭제 전 프로젝트 연결 여부만 확인한다. */
@Entity
@Table(name = "project_team")
@Immutable
@Getter
@NoArgsConstructor
public class TeamProjectRefEntity {

    @EmbeddedId
    private TeamProjectRefId id;
}
