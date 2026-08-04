package com.module06.backend.handover.presentation.api.response;

import com.module06.backend.handover.domain.model.Handover;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;

public record HandoverResponse(
        Long id,
        Long writerMemberId,
        Long teamId,
        HandoverType handoverType,
        HandoverStatus status,
        Long intermediateApproverId,
        String intermediateApproverNameSnap,
        Long finalApproverId,
        String finalApproverNameSnap,
        String rejectReason
) {

    public static HandoverResponse from(Handover handover) {
        return new HandoverResponse(
                handover.getId(),
                handover.getWriterMemberId(),
                handover.getTeamId(),
                handover.getHandoverType(),
                handover.getStatus(),
                handover.getIntermediateApproverId(),
                handover.getIntermediateApproverNameSnap(),
                handover.getFinalApproverId(),
                handover.getFinalApproverNameSnap(),
                handover.getRejectReason()
        );
    }
}
