package com.module06.backend.handover.presentation.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FinalizeHandoverRequest(
        @NotNull Long approverId,
        @NotBlank String approverName
) {
}
