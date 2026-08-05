package com.module06.backend.handover.infrastructure.persistence;

import com.module06.backend.handover.domain.model.HandoverInsight;
import com.module06.backend.handover.domain.model.HandoverInsightKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

@Entity
@Table(name = "handover_insight")
public class HandoverInsightJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "handover_id", nullable = false)
    private Long handoverId;

    @Column(name = "action_id")
    private Long actionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 32)
    private HandoverInsightKind kind;

    @Column(name = "payload", nullable = false, columnDefinition = "json")
    private String payload;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected HandoverInsightJpaEntity() {
    }

    private HandoverInsightJpaEntity(
        Long id,
        Long handoverId,
        Long actionId,
        HandoverInsightKind kind,
        String payload,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this.id = id;
        this.handoverId = handoverId;
        this.actionId = actionId;
        this.kind = kind;
        this.payload = payload;
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    static HandoverInsightJpaEntity from(HandoverInsight insight) {
        return new HandoverInsightJpaEntity(
            insight.getId(),
            insight.getHandoverId(),
            insight.getActionId(),
            insight.getKind(),
            insight.getPayload(),
            insight.getSortOrder(),
            insight.getCreatedAt(),
            insight.getUpdatedAt()
        );
    }

    HandoverInsight toDomain() {
        return HandoverInsight.restore(id, handoverId, actionId, kind, payload, sortOrder, createdAt, updatedAt);
    }

    @PrePersist
    void prePersist() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
