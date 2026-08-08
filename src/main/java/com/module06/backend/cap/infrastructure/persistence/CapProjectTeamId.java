package com.module06.backend.cap.infrastructure.persistence;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Embeddable;
import lombok.Getter;

/* comment.
    project_team 복합 PK(project_id, team_id). JPA @EmbeddedId 대상 — project.ProjectTeamId,
    team.TeamProjectRefId와 동일 테이블을 매핑한다.

    ⚠️ 여기서는 일부러 @Column(name=...)을 안 붙인다. 같은 테이블을 매핑하는 다른 두 엔티티가
    전부 암묵적 네이밍(camelCase→snake_case)을 쓰는데 여기만 명시하면, Hibernate가 같은 물리
    컬럼을 서로 다른 논리 컬럼명(projectId vs project_id)으로 인식해 컨텍스트가 죽는다
    (DuplicateMappingException, 이 파일 최초 작성 시 실제로 겪음). MeetingAttendeeId 때와 반대
    결론이라 헷갈리지만, 그쪽은 같은 테이블을 매핑하는 다른 엔티티가 없어서 명시가 안전했던 것뿐이다.
*/
@Getter
@Embeddable
public class CapProjectTeamId implements Serializable {

    private Long projectId;

    private Long teamId;

    protected CapProjectTeamId() {
    }

    public CapProjectTeamId(Long projectId, Long teamId) {
        this.projectId = projectId;
        this.teamId = teamId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CapProjectTeamId that)) return false;
        return Objects.equals(projectId, that.projectId) && Objects.equals(teamId, that.teamId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(projectId, teamId);
    }
}
