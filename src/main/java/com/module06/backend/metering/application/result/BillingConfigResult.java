package com.module06.backend.metering.application.result;

public record BillingConfigResult(
        int baseFee,
        long includedTokens,
        long includedStorageGb,
        int overagePerThousandTokens,
        int overagePerGbMonth,
        boolean isVatIncluded
) {
}
