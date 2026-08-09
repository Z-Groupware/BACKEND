package com.module06.backend.project.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.module06.backend.project.domain.model.ProjectStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    project 테이블 JPA 매핑. project_team(지정 부서)은 별도 ProjectTeamJpaEntity로 관리하며
    이 엔티티와 JPA 연관관계를 맺지 않는다 — 어댑터가 두 저장소를 수동으로 조율한다.
*/
@Entity
@Table(name = "project")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "tag", nullable = false, length = 30)
    private String tag;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "color", nullable = false, length = 7)
    private String color;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ProjectStatus status;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Builder
    private ProjectJpaEntity(
            Long id,
            Long companyId,
            String tag,
            String name,
            String description,
            String color,
            ProjectStatus status,
            LocalDate dueDate,
            Long createdBy,
            LocalDateTime deletedAt
    ) {
        this.id = id;
        this.companyId = companyId;
        this.tag = tag;
        this.name = name;
        this.description = description;
        this.color = color;
        this.status = status;
        this.dueDate = dueDate;
        this.createdBy = createdBy;
        this.deletedAt = deletedAt;
    }
}
