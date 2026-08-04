package com.module06.backend.handover.application.command;

import java.time.LocalDateTime;

public record ReassignItemCommand(
        Long handoverId,
        Long actionId,
        Long toMemberId,
        LocalDateTime reassignedAt
) {
}
