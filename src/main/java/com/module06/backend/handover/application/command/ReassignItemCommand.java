package com.module06.backend.handover.application.command;

import com.module06.backend.global.security.AuthPrincipal;

import java.time.LocalDateTime;

public record ReassignItemCommand(
        Long handoverId,
        Long actionId,
        Long toMemberId,
        LocalDateTime reassignedAt,
        AuthPrincipal requester
) {
}
