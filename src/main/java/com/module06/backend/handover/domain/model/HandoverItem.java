package com.module06.backend.handover.domain.model;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.handover.domain.exception.HandoverErrorCode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

public class HandoverItem {

    private static final String ROLLBACK_STATUS_ROLLED_BACK = "ROLLED_BACK";

    private final Long id;
    private final Long actionId;
    private final String actionTitleSnap;
    private final String actionStatusSnap;
    private final String projectTagSnap;
    private final String actionTypeSnap;
    private final LocalDate deadlineSnap;
    private final LocalDateTime actionCreatedAtSnap;
    private final Long sourceMeetingId;
    private final String sourceMeetingTitleSnap;
    private final String contentSnap;
    private final boolean reassignRequired;
    private Long reassigneeId;
    private String reassigneeNameSnap;
    private String reassigneePositionSnap;
    private LocalDateTime reassignedAt;
    // 갭1(반려 시 자동 롤백) 준비 필드. 실제 커밋/롤백은 C rollbackReassignment 계약 대기.
    private LocalDateTime committedAt;
    private String rollbackStatus;

    public HandoverItem(Long id, Long actionId, String actionTitleSnap, String actionStatusSnap,
                        String projectTagSnap, String actionTypeSnap, LocalDate deadlineSnap,
                        LocalDateTime actionCreatedAtSnap,
                        Long sourceMeetingId, String sourceMeetingTitleSnap, String contentSnap,
                        Long reassigneeId, String reassigneeNameSnap, String reassigneePositionSnap,
                        LocalDateTime reassignedAt, LocalDateTime committedAt, String rollbackStatus,
                        boolean reassignRequired) {
        requireId(actionId, "actionId");
        requireText(actionTitleSnap, "actionTitleSnap");
        requireText(actionStatusSnap, "actionStatusSnap");
        requireText(actionTypeSnap, "actionTypeSnap");
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
        this.reassignRequired = reassignRequired;
        this.reassigneeId = reassigneeId;
        this.reassigneeNameSnap = reassigneeNameSnap;
        this.reassigneePositionSnap = reassigneePositionSnap;
        this.reassignedAt = reassignedAt;
        this.committedAt = committedAt;
        this.rollbackStatus = rollbackStatus;
    }

    public static HandoverItem create(Long actionId, String actionTitleSnap, String actionStatusSnap,
                                      String projectTagSnap, String actionTypeSnap, LocalDate deadlineSnap,
                                      LocalDateTime actionCreatedAtSnap, Long sourceMeetingId,
                                      String sourceMeetingTitleSnap, String contentSnap,
                                      boolean reassignRequired) {
        return new HandoverItem(null, actionId, actionTitleSnap, actionStatusSnap, projectTagSnap,
                actionTypeSnap, deadlineSnap, actionCreatedAtSnap, sourceMeetingId, sourceMeetingTitleSnap, contentSnap,
                null, null, null, null, null, null, reassignRequired);
    }

    public void reassignTo(Long memberId, String nameSnap, String positionSnap, LocalDateTime at) {
        requireId(memberId, "memberId");
        requireText(nameSnap, "nameSnap");
        requireText(positionSnap, "positionSnap");
        if (at == null) {
            throw new BusinessException(HandoverErrorCode.HO_REASSIGNED_AT_REQUIRED);
        }
        this.reassigneeId = memberId;
        this.reassigneeNameSnap = nameSnap;
        this.reassigneePositionSnap = positionSnap;
        this.reassignedAt = at;
    }

    public boolean isReassigned() {
        return reassigneeId != null;
    }

    public void commit(LocalDateTime at) {
        if (at == null) {
            throw new BusinessException(HandoverErrorCode.HO_APPROVED_AT_REQUIRED);
        }
        this.committedAt = at;
    }

    public void markRolledBack() {
        this.rollbackStatus = ROLLBACK_STATUS_ROLLED_BACK;
    }

    public boolean isCommitted() {
        return committedAt != null;
    }

    public boolean hasActionId(Long actionId) {
        return Objects.equals(this.actionId, actionId);
    }

    private static void requireId(Long value, String name) {
        if (value == null) {
            throw new BusinessException(HandoverErrorCode.HO_REQUIRED_ID);
        }
    }

    private static void requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(HandoverErrorCode.HO_REQUIRED_TEXT);
        }
    }

    public Long getId() { return id; }
    public Long getActionId() { return actionId; }
    public String getActionTitleSnap() { return actionTitleSnap; }
    public String getActionStatusSnap() { return actionStatusSnap; }
    public String getProjectTagSnap() { return projectTagSnap; }
    public String getActionTypeSnap() { return actionTypeSnap; }
    public LocalDate getDeadlineSnap() { return deadlineSnap; }
    public LocalDateTime getActionCreatedAtSnap() { return actionCreatedAtSnap; }
    public Long getSourceMeetingId() { return sourceMeetingId; }
    public String getSourceMeetingTitleSnap() { return sourceMeetingTitleSnap; }
    public String getContentSnap() { return contentSnap; }
    public boolean isReassignRequired() { return reassignRequired; }
    public Long getReassigneeId() { return reassigneeId; }
    public String getReassigneeNameSnap() { return reassigneeNameSnap; }
    public String getReassigneePositionSnap() { return reassigneePositionSnap; }
    public LocalDateTime getReassignedAt() { return reassignedAt; }
    public LocalDateTime getCommittedAt() { return committedAt; }
    public String getRollbackStatus() { return rollbackStatus; }
}
