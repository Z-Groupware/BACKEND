package com.module06.backend.metering.presentation.api.dto.request;

public record RegisterPaymentMethodRequest(
        String authKey,
        String customerKey
) {
}
