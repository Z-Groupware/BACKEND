package com.module06.backend.handover.presentation.api.dto.response;

import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record HandoverResponse(
        Long id,
        Long writerMemberId,
        Long teamId,
        String teamNameSnap,
        HandoverType handoverType,
        HandoverStatus status,
        LocalDateTime leaveStartAt,
        LocalDateTime leaveEndAt,
        LocalDate lastWorkingDay,
        String writerNameSnap,
        String writerPositionSnap,
        Long intermediateApproverId,
        String intermediateApproverNameSnap,
        LocalDateTime intermediateApprovedAt,
        String rejectReason,
        LocalDateTime finalizedAt,
        Long finalApproverId,
        String finalApproverNameSnap,
        Long version,
        List<HandoverItemResponse> items
) {

    public static HandoverResponse from(Handover handover) {
        return new HandoverResponse(
                handover.getId(),
                handover.getWriterMemberId(),
                handover.getTeamId(),
                handover.getTeamNameSnap(),
                handover.getHandoverType(),
                handover.getStatus(),
                handover.getLeaveStartAt(),
                handover.getLeaveEndAt(),
                handover.getLastWorkingDay(),
                handover.getWriterNameSnap(),
                handover.getWriterPositionSnap(),
                handover.getIntermediateApproverId(),
                handover.getIntermediateApproverNameSnap(),
                handover.getIntermediateApprovedAt(),
                handover.getRejectReason(),
                handover.getFinalizedAt(),
                handover.getFinalApproverId(),
                handover.getFinalApproverNameSnap(),
                handover.getVersion(),
                handover.getItems().stream()
                        .map(HandoverItemResponse::from)
                        .toList()
        );
    }
}
