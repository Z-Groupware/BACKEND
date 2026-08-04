package com.module06.backend.handover.application.command;

import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.util.List;

public record CreateHandoverCommand(
        Long writerMemberId,
        Long teamId,
        HandoverType handoverType,
        LocalDate leaveStartAt,
        LocalDate leaveEndAt,
        LocalDate lastWorkingDay,
        List<Long> selectedActionIds
) {
}
