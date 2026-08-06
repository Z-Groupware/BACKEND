package com.module06.backend.identity.team.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;

@Embeddable
public class TeamProjectRefId implements Serializable {

    private Long projectId;
    private Long teamId;

    protected TeamProjectRefId() {
    }

    public TeamProjectRefId(Long projectId, Long teamId) {
        this.projectId = projectId;
        this.teamId = teamId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamProjectRefId that)) return false;
        return Objects.equals(projectId, that.projectId) && Objects.equals(teamId, that.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, teamId);
    }
}
