package com.module06.backend.handover.presentation.api.request;

import jakarta.validation.constraints.NotNull;

public record CompleteHandoverRequest(
        @NotNull Long leaderId
) {
}
