package com.module06.backend.metering.application.result;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record BillingPaymentActionResult(
        boolean isSuccess,
        String failureCode
) {

    public static BillingPaymentActionResult success() {
        return new BillingPaymentActionResult(true, null);
    }

    public static BillingPaymentActionResult failure(String failureCode) {
        return new BillingPaymentActionResult(false, failureCode);
    }
}
