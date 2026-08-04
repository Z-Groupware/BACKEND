package com.module06.backend.handover.presentation.api.dto.request;

import com.module06.backend.handover.application.command.CreateHandoverCommand;
import com.module06.backend.handover.domain.model.HandoverType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record CreateHandoverRequest(
        @NotNull Long writerMemberId,
        @NotNull Long teamId,
        @NotNull HandoverType handoverType,
        LocalDateTime leaveStartAt,
        LocalDateTime leaveEndAt,
        LocalDate lastWorkingDay,
        List<Long> selectedActionIds
) {

    public CreateHandoverCommand toCommand() {
        return new CreateHandoverCommand(
                writerMemberId,
                teamId,
                handoverType,
                leaveStartAt,
                leaveEndAt,
                lastWorkingDay,
                selectedActionIds
        );
    }
}
