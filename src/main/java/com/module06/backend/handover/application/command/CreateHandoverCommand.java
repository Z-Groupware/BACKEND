package com.module06.backend.handover.application.command;

import com.module06.backend.handover.domain.model.HandoverType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreateHandoverCommand(
        Long writerMemberId,
        Long teamId,
        HandoverType handoverType,
        LocalDateTime leaveStartAt,
        LocalDateTime leaveEndAt,
        LocalDate lastWorkingDay,
        String note,
        List<Long> selectedActionIds
) {
}
