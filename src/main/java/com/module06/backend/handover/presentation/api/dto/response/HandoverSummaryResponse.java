package com.module06.backend.handover.presentation.api.dto.response;

import com.module06.backend.handover.application.usecase.GetHandoverListUseCase;
import com.module06.backend.handover.domain.model.HandoverStatus;
import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record HandoverSummaryResponse(
        Long id,
        Long writerMemberId,
        String writerName,
        String writerPosition,
        Long teamId,
        HandoverType handoverType,
        HandoverStatus status,
        LocalDateTime leaveStartAt,
        LocalDateTime leaveEndAt,
        LocalDate lastWorkingDay,
        int itemCount,
        int reassignRequiredCount,
        int reassignedCount
) {

    public static HandoverSummaryResponse from(GetHandoverListUseCase.HandoverSummary summary) {
        return new HandoverSummaryResponse(
                summary.id(),
                summary.writerMemberId(),
                summary.writerName(),
                summary.writerPosition(),
                summary.teamId(),
                summary.handoverType(),
                summary.status(),
                summary.leaveStartAt(),
                summary.leaveEndAt(),
                summary.lastWorkingDay(),
                summary.itemCount(),
                summary.reassignRequiredCount(),
                summary.reassignedCount()
        );
    }
}
