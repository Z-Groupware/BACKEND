package com.module06.backend.handover.infrastructure.persistence;

import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "handover")
public class HandoverJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "writer_member_id", nullable = false)
    private Long writerMemberId;

    @Column(name = "team_id", nullable = false)
    private Long teamId;

    @Column(name = "team_name_snap")
    private String teamNameSnap;

    @Enumerated(EnumType.STRING)
    @Column(name = "handover_type", nullable = false, length = 20)
    private HandoverType handoverType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private HandoverStatus status;

    @Column(name = "leave_start_at")
    private LocalDateTime leaveStartAt;

    @Column(name = "leave_end_at")
    private LocalDateTime leaveEndAt;

    @Column(name = "last_working_day")
    private LocalDate lastWorkingDay;

    @Column(name = "writer_name_snap", nullable = false)
    private String writerNameSnap;

    @Column(name = "writer_position_snap", nullable = false)
    private String writerPositionSnap;

    @Column(name = "intermediate_approver_id")
    private Long intermediateApproverId;

    @Column(name = "intermediate_approver_name_snap")
    private String intermediateApproverNameSnap;

    @Column(name = "intermediate_approved_at")
    private LocalDateTime intermediateApprovedAt;

    @Column(name = "reject_reason")
    private String rejectReason;

    @Column(name = "finalized_at")
    private LocalDateTime finalizedAt;

    @Column(name = "final_approver_id")
    private Long finalApproverId;

    @Column(name = "final_approver_name_snap")
    private String finalApproverNameSnap;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "handover", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<HandoverItemJpaEntity> items = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // TODO: global BaseTimeEntity can replace createdAt/updatedAt after shared contracts are available.

    protected HandoverJpaEntity() {
    }

    static HandoverJpaEntity fromDomain(Handover handover) {
        HandoverJpaEntity entity = new HandoverJpaEntity();
        entity.id = handover.getId();
        entity.writerMemberId = handover.getWriterMemberId();
        entity.teamId = handover.getTeamId();
        entity.teamNameSnap = handover.getTeamNameSnap();
        entity.handoverType = handover.getHandoverType();
        entity.status = handover.getStatus();
        entity.leaveStartAt = handover.getLeaveStartAt();
        entity.leaveEndAt = handover.getLeaveEndAt();
        entity.lastWorkingDay = handover.getLastWorkingDay();
        entity.writerNameSnap = handover.getWriterNameSnap();
        entity.writerPositionSnap = handover.getWriterPositionSnap();
        entity.intermediateApproverId = handover.getIntermediateApproverId();
        entity.intermediateApproverNameSnap = handover.getIntermediateApproverNameSnap();
        entity.intermediateApprovedAt = handover.getIntermediateApprovedAt();
        entity.rejectReason = handover.getRejectReason();
        entity.finalizedAt = handover.getFinalizedAt();
        entity.finalApproverId = handover.getFinalApproverId();
        entity.finalApproverNameSnap = handover.getFinalApproverNameSnap();
        entity.version = handover.getVersion();
        entity.items = new ArrayList<>(handover.getItems().stream()
                .map(HandoverItemJpaEntity::fromDomain)
                .toList());
        entity.items.forEach(item -> item.attachTo(entity));
        return entity;
    }

    Handover toDomain() {
        return Handover.restore(id, writerMemberId, teamId, teamNameSnap, handoverType, status, leaveStartAt, leaveEndAt,
                lastWorkingDay,
                writerNameSnap, writerPositionSnap, intermediateApproverId, intermediateApproverNameSnap,
                intermediateApprovedAt, rejectReason, finalizedAt, finalApproverId, finalApproverNameSnap, version,
                items.stream().map(HandoverItemJpaEntity::toDomain).toList());
    }

    Long getWriterMemberId() {
        return writerMemberId;
    }

    Long getTeamId() {
        return teamId;
    }

    HandoverStatus getStatus() {
        return status;
    }
}
