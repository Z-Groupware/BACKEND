package com.module06.backend.handover.domain.model;

import java.time.LocalDateTime;
import java.util.Objects;

public class HandoverInsight {

    private final Long id;
    private final Long handoverId;
    private final Long actionId;
    private final HandoverInsightKind kind;
    private final String payload;
    private final int sortOrder;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private HandoverInsight(
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
        this.handoverId = Objects.requireNonNull(handoverId, "handoverId must not be null");
        this.actionId = actionId;
        this.kind = Objects.requireNonNull(kind, "kind must not be null");
        this.payload = Objects.requireNonNull(payload, "payload must not be null");
        this.sortOrder = sortOrder;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public static HandoverInsight newSnapshot(
        Long handoverId,
        Long actionId,
        HandoverInsightKind kind,
        String payload,
        int sortOrder
    ) {
        return new HandoverInsight(null, handoverId, actionId, kind, payload, sortOrder, null, null);
    }

    public static HandoverInsight restore(
        Long id,
        Long handoverId,
        Long actionId,
        HandoverInsightKind kind,
        String payload,
        int sortOrder,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        return new HandoverInsight(id, handoverId, actionId, kind, payload, sortOrder, createdAt, updatedAt);
    }

    public Long getId() {
        return id;
    }

    public Long getHandoverId() {
        return handoverId;
    }

    public Long getActionId() {
        return actionId;
    }

    public HandoverInsightKind getKind() {
        return kind;
    }

    public String getPayload() {
        return payload;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
