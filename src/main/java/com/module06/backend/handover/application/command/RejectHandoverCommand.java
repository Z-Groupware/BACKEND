package com.module06.backend.handover.application.command;

public record RejectHandoverCommand(
        Long handoverId,
        String reason
) {
}
