package com.module06.backend.action.infrastructure.persistence;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.module06.backend.action.domain.model.Action;
import com.module06.backend.action.domain.model.ActionStatus;
import com.module06.backend.action.domain.model.ActionType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/* comment.
    action 테이블 JPA 매핑. 도메인 모델 Action과 1:1로 변환된다.
    매핑 대상 컬럼: id·company_id·project_id·parent_action_id·source_meeting_id·team_id·
    assignee_member_id·action_type·title·description·status·due_date·needs_review·
    confirmed_at·created_at·updated_at (V1__init_schema.sql 기준).
    다른 도메인 엔티티를 @ManyToOne으로 물지 않는다 — project_id·team_id·assignee_member_id·
    source_meeting_id는 전부 id 값으로만 둔다(0절 1항).
    스키마 주인은 Flyway이므로 ddl-auto는 validate 이상으로 올리지 않는다.
    TEAM/PERSONAL 자기참조(parent_action_id)를 이 엔티티도 그대로 반영한다 — @ManyToOne self-join이 아니라
    id 값 컬럼으로만 둔다(도메인 내부 참조라도 0절 1항과 동일한 원칙 적용).

    연결된 클래스
    - Action                      : 변환 대상 도메인 모델
    - SpringDataActionRepository  : 이 엔티티를 다루는 Spring Data 인터페이스
    - ActionPersistenceAdapter    : 도메인 ↔ 엔티티 변환 담당
    - MemberReferenceEntity · TeamReferenceEntity · ProjectReferenceEntity : 조인 시 함께 읽는 참조 엔티티
*/
@Entity
@Table(name = "action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ActionJpaEntity {

    @Id
    private Long id;

    @Column(name = "company_id", nullable = false)
    private Long companyId;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "parent_action_id")
    private Long parentActionId;

    @Column(name = "source_meeting_id")
    private Long sourceMeetingId;

    @Column(name = "team_id")
    private Long teamId;

    @Column(name = "assignee_member_id")
    private Long assigneeMemberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private ActionType actionType;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ActionStatus status;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ActionJpaEntity(
            Long id,
            Long companyId,
            Long projectId,
            Long parentActionId,
            Long sourceMeetingId,
            Long teamId,
            Long assigneeMemberId,
            ActionType actionType,
            String title,
            String description,
            ActionStatus status,
            LocalDate dueDate,
            LocalDateTime confirmedAt,
            LocalDateTime createdAt,
            LocalDateTime updatedAt
    ) {
        this.id = id;
        this.companyId = companyId;
        this.projectId = projectId;
        this.parentActionId = parentActionId;
        this.sourceMeetingId = sourceMeetingId;
        this.teamId = teamId;
        this.assigneeMemberId = assigneeMemberId;
        this.actionType = actionType;
        this.title = title;
        this.description = description;
        this.status = status;
        this.dueDate = dueDate;
        this.confirmedAt = confirmedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static ActionJpaEntity from(Action action) {
        return new ActionJpaEntity(
                action.getId(),
                action.getCompanyId(),
                action.getProjectId(),
                action.getParentActionId(),
                action.getSourceMeetingId(),
                action.getTeamId(),
                action.getAssigneeMemberId(),
                action.getActionType(),
                action.getTitle(),
                action.getDescription(),
                action.getStatus(),
                action.getDueDate(),
                action.getConfirmedAt(),
                action.getCreatedAt(),
                action.getUpdatedAt()
        );
    }

    public Action toDomain() {
        return new Action(
                id,
                companyId,
                projectId,
                parentActionId,
                sourceMeetingId,
                teamId,
                assigneeMemberId,
                actionType,
                title,
                description,
                status,
                dueDate,
                confirmedAt,
                createdAt,
                updatedAt
        );
    }
}
