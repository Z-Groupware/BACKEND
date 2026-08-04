package com.module06.backend.project.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import lombok.Getter;

/* comment.
    project_team 복합 PK(project_id, team_id). JPA @EmbeddedId 대상.
*/
@Getter
@Embeddable
public class ProjectTeamId implements Serializable {

    private Long projectId;
    private Long teamId;

    protected ProjectTeamId() {
    }

    public ProjectTeamId(Long projectId, Long teamId) {
        this.projectId = projectId;
        this.teamId = teamId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectTeamId that)) return false;
        return Objects.equals(projectId, that.projectId) && Objects.equals(teamId, that.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, teamId);
    }
}
