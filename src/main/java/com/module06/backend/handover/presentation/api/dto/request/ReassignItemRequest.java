package com.module06.backend.handover.presentation.api.dto.request;

import com.module06.backend.handover.application.command.ReassignItemCommand;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record ReassignItemRequest(
        @NotNull Long toMemberId
) {

    public ReassignItemCommand toCommand(Long handoverId, Long actionId, LocalDateTime reassignedAt) {
        return new ReassignItemCommand(handoverId, actionId, toMemberId, reassignedAt);
    }
}
