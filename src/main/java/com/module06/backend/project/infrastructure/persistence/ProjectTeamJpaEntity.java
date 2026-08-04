package com.module06.backend.project.infrastructure.persistence;

import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    project_team 조인 테이블 JPA 매핑(B안 — 별도 엔티티, 2026-08-05 확정). Project와 JPA
    연관관계를 맺지 않고 projectId 값만 복합키에 들고 있다 — ProjectPersistenceAdapter가 수동 조율.
*/
@Entity
@Table(name = "project_team")
@Getter
@NoArgsConstructor
public class ProjectTeamJpaEntity {

    @EmbeddedId
    private ProjectTeamId id;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ProjectTeamJpaEntity(Long projectId, Long teamId) {
        this.id = new ProjectTeamId(projectId, teamId);
    }
}
