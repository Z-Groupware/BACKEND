package com.module06.backend.handover.presentation.api.dto.request;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.handover.application.command.RejectHandoverCommand;
import jakarta.validation.constraints.NotBlank;

public record RejectHandoverRequest(
        @NotBlank String reason
) {

    public RejectHandoverCommand toCommand(Long handoverId, AuthPrincipal requester) {
        return new RejectHandoverCommand(handoverId, reason, requester);
    }
}
