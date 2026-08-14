package com.module06.backend.handover.application.command;

import com.module06.backend.global.security.AuthPrincipal;

public record RejectHandoverCommand(
        Long handoverId,
        String reason,
        AuthPrincipal requester
) {
}
