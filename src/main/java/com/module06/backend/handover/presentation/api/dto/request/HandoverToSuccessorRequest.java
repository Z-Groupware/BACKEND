package com.module06.backend.handover.presentation.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record HandoverToSuccessorRequest(
        @NotNull Long successorId,
        @NotNull Long ownerId,
        @NotBlank String ownerName
) {
}
