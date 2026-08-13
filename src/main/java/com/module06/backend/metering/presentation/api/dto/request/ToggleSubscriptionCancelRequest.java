package com.module06.backend.metering.presentation.api.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ToggleSubscriptionCancelRequest(
        @JsonProperty("isCanceling")
        boolean isCanceling
) {
}
