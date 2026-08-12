package com.module06.backend.handover.infrastructure.persistence;

import com.module06.backend.handover.domain.model.HandoverItem;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "handover_item")
public class HandoverItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "handover_id", nullable = false)
    private HandoverJpaEntity handover;

    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @Column(name = "action_title_snap", nullable = false)
    private String actionTitleSnap;

    @Column(name = "action_status_snap", nullable = false)
    private String actionStatusSnap;

    @Column(name = "project_tag_snap")
    private String projectTagSnap;

    @Column(name = "action_type_snap", nullable = false)
    private String actionTypeSnap;

    @Column(name = "deadline_snap")
    private LocalDate deadlineSnap;

    @Column(name = "action_created_at_snap")
    private LocalDateTime actionCreatedAtSnap;

    @Column(name = "source_meeting_id")
    private Long sourceMeetingId;

    @Column(name = "source_meeting_title_snap")
    private String sourceMeetingTitleSnap;

    // V7.0 마이그레이션이 TEXT(65,535바이트)로 만든 컬럼. @Lob에 길이가 없으면 Hibernate가 기본 255로
    // 보고 tinytext를 기대해 ddl-auto=validate가 부팅을 막는다. 길이를 명시해 TEXT로 맞춘다.
    @Column(name = "content_snap", length = 65535)
    private String contentSnap;

    @Column(name = "parent_action_title_snap")
    private String parentActionTitleSnap;

    @Column(name = "start_date_snap")
    private LocalDate startDateSnap;

    @Column(name = "reassign_required", nullable = false)
    private boolean reassignRequired = true;

    @Column(name = "reassignee_id")
    private Long reassigneeId;

    @Column(name = "reassignee_name_snap")
    private String reassigneeNameSnap;

    @Column(name = "reassignee_position_snap")
    private String reassigneePositionSnap;

    @Column(name = "reassigned_at")
    private LocalDateTime reassignedAt;

    @Column(name = "committed_at")
    private LocalDateTime committedAt;

    @Column(name = "rollback_status", length = 20)
    private String rollbackStatus;

    protected HandoverItemJpaEntity() {
    }

    private HandoverItemJpaEntity(Long id, Long actionId, String actionTitleSnap, String actionStatusSnap,
                                  String projectTagSnap, String actionTypeSnap, LocalDate deadlineSnap,
                                  LocalDateTime actionCreatedAtSnap,
                                  Long sourceMeetingId, String sourceMeetingTitleSnap, String contentSnap,
                                  String parentActionTitleSnap, LocalDate startDateSnap,
                                  boolean reassignRequired,
                                  Long reassigneeId, String reassigneeNameSnap,
                                  String reassigneePositionSnap, LocalDateTime reassignedAt,
                                  LocalDateTime committedAt, String rollbackStatus) {
        this.id = id;
        this.actionId = actionId;
        this.actionTitleSnap = actionTitleSnap;
        this.actionStatusSnap = actionStatusSnap;
        this.projectTagSnap = projectTagSnap;
        this.actionTypeSnap = actionTypeSnap;
        this.deadlineSnap = deadlineSnap;
        this.actionCreatedAtSnap = actionCreatedAtSnap;
        this.sourceMeetingId = sourceMeetingId;
        this.sourceMeetingTitleSnap = sourceMeetingTitleSnap;
        this.contentSnap = contentSnap;
        this.parentActionTitleSnap = parentActionTitleSnap;
        this.startDateSnap = startDateSnap;
        this.reassignRequired = reassignRequired;
        this.reassigneeId = reassigneeId;
        this.reassigneeNameSnap = reassigneeNameSnap;
        this.reassigneePositionSnap = reassigneePositionSnap;
        this.reassignedAt = reassignedAt;
        this.committedAt = committedAt;
        this.rollbackStatus = rollbackStatus;
    }

    static HandoverItemJpaEntity fromDomain(HandoverItem item) {
        return new HandoverItemJpaEntity(item.getId(), item.getActionId(), item.getActionTitleSnap(),
                item.getActionStatusSnap(), item.getProjectTagSnap(), item.getActionTypeSnap(),
                item.getDeadlineSnap(), item.getActionCreatedAtSnap(), item.getSourceMeetingId(), item.getSourceMeetingTitleSnap(),
                item.getContentSnap(), item.getParentActionTitleSnap(), item.getStartDateSnap(),
                item.isReassignRequired(), item.getReassigneeId(), item.getReassigneeNameSnap(), item.getReassigneePositionSnap(),
                item.getReassignedAt(), item.getCommittedAt(), item.getRollbackStatus());
    }

    HandoverItem toDomain() {
        return new HandoverItem(id, actionId, actionTitleSnap, actionStatusSnap, projectTagSnap, actionTypeSnap,
                deadlineSnap, actionCreatedAtSnap, sourceMeetingId, sourceMeetingTitleSnap, contentSnap,
                parentActionTitleSnap, startDateSnap,
                reassigneeId, reassigneeNameSnap, reassigneePositionSnap, reassignedAt, committedAt, rollbackStatus,
                reassignRequired);
    }

    void attachTo(HandoverJpaEntity handover) {
        this.handover = handover;
    }
}
