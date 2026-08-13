package com.module06.backend.metering.application.result;

import com.module06.backend.metering.domain.model.BillingPaymentStatus;
import com.module06.backend.metering.domain.model.BillingSubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BillingOverviewResult(
        SubscriptionResult subscription,
        PaymentMethodResult method,
        List<PaymentRecordResult> payments
) {

    public record SubscriptionResult(
            String planCode,
            String planName,
            BillingSubscriptionStatus status,
            LocalDate currentPeriodStart,
            LocalDate currentPeriodEnd,
            LocalDate nextBillingDate,
            int carriedOverageAmount,
            int estimatedAmount,
            UsageResult usage
    ) {
    }

    public record UsageResult(
            long tokens,
            double voiceStorageGb,
            double sttStorageGb,
            long meetingCount
    ) {
    }

    public record PaymentMethodResult(
            Long id,
            String brand,
            String last4,
            String expiry
    ) {
    }

    public record PaymentRecordResult(
            Long id,
            LocalDate paidAt,
            String planName,
            BigDecimal overageAmount,
            BigDecimal amount,
            BillingPaymentStatus status
    ) {
    }
}
