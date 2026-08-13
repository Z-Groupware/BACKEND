package com.module06.backend.metering.presentation.api.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.module06.backend.metering.application.result.BillingConfigResult;

public record BillingConfigResponse(
        int baseFee,
        long includedTokens,
        long includedStorageGb,
        int overagePerThousandTokens,
        int overagePerGbMonth,
        @JsonProperty("isVatIncluded")
        boolean isVatIncluded
) {
    public static BillingConfigResponse from(BillingConfigResult result) {
        return new BillingConfigResponse(
                result.baseFee(),
                result.includedTokens(),
                result.includedStorageGb(),
                result.overagePerThousandTokens(),
                result.overagePerGbMonth(),
                result.isVatIncluded());
    }
}
