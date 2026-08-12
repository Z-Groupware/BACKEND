package com.module06.backend.handover.presentation.api.dto.response;

import com.module06.backend.handover.domain.model.HandoverItem;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HandoverItemResponse(
        Long id,
        Long actionId,
        String actionTitleSnap,
        String actionStatusSnap,
        String projectTagSnap,
        String actionTypeSnap,
        LocalDate deadlineSnap,
        LocalDateTime actionCreatedAtSnap,
        Long sourceMeetingId,
        String sourceMeetingTitleSnap,
        String contentSnap,
        String parentActionTitleSnap,
        LocalDate startDateSnap,
        boolean reassignRequired,
        Long reassigneeId,
        String reassigneeNameSnap,
        String reassigneePositionSnap,
        LocalDateTime reassignedAt,
        LocalDateTime committedAt,
        String rollbackStatus
) {

    public static HandoverItemResponse from(HandoverItem item) {
        return new HandoverItemResponse(
                item.getId(),
                item.getActionId(),
                item.getActionTitleSnap(),
                item.getActionStatusSnap(),
                item.getProjectTagSnap(),
                item.getActionTypeSnap(),
                item.getDeadlineSnap(),
                item.getActionCreatedAtSnap(),
                item.getSourceMeetingId(),
                item.getSourceMeetingTitleSnap(),
                item.getContentSnap(),
                item.getParentActionTitleSnap(),
                item.getStartDateSnap(),
                item.isReassignRequired(),
                item.getReassigneeId(),
                item.getReassigneeNameSnap(),
                item.getReassigneePositionSnap(),
                item.getReassignedAt(),
                item.getCommittedAt(),
                item.getRollbackStatus()
        );
    }
}
