package com.module06.backend.metering.presentation.api.dto.response;

import com.module06.backend.metering.application.result.BillingOverviewResult;
import com.module06.backend.metering.domain.model.BillingPaymentStatus;
import com.module06.backend.metering.domain.model.BillingSubscriptionStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record BillingOverviewResponse(
        SubscriptionResponse subscription,
        PaymentMethodResponse method,
        List<PaymentRecordResponse> payments
) {

    public static BillingOverviewResponse from(BillingOverviewResult result) {
        return new BillingOverviewResponse(
                SubscriptionResponse.from(result.subscription()),
                result.method() == null ? null : PaymentMethodResponse.from(result.method()),
                result.payments().stream().map(PaymentRecordResponse::from).toList()
        );
    }

    public record SubscriptionResponse(
            String planCode,
            String planName,
            BillingSubscriptionStatus status,
            LocalDate currentPeriodStart,
            LocalDate currentPeriodEnd,
            LocalDate nextBillingDate,
            int carriedOverageAmount,
            int estimatedAmount,
            UsageResponse usage
    ) {
        static SubscriptionResponse from(BillingOverviewResult.SubscriptionResult result) {
            return new SubscriptionResponse(
                    result.planCode(),
                    result.planName(),
                    result.status(),
                    result.currentPeriodStart(),
                    result.currentPeriodEnd(),
                    result.nextBillingDate(),
                    result.carriedOverageAmount(),
                    result.estimatedAmount(),
                    UsageResponse.from(result.usage())
            );
        }
    }

    public record UsageResponse(
            long tokens,
            double voiceStorageGb,
            double sttStorageGb,
            long meetingCount
    ) {
        static UsageResponse from(BillingOverviewResult.UsageResult result) {
            return new UsageResponse(
                    result.tokens(),
                    result.voiceStorageGb(),
                    result.sttStorageGb(),
                    result.meetingCount()
            );
        }
    }

    public record PaymentMethodResponse(
            String id,
            String brand,
            String last4,
            String expiry
    ) {
        static PaymentMethodResponse from(BillingOverviewResult.PaymentMethodResult result) {
            return new PaymentMethodResponse(
                    "pm_" + result.id(),
                    result.brand(),
                    result.last4(),
                    result.expiry()
            );
        }
    }

    public record PaymentRecordResponse(
            String id,
            LocalDate paidAt,
            String planName,
            BigDecimal overageAmount,
            BigDecimal amount,
            BillingPaymentStatus status
    ) {
        static PaymentRecordResponse from(BillingOverviewResult.PaymentRecordResult result) {
            return new PaymentRecordResponse(
                    "pay_" + result.id(),
                    result.paidAt(),
                    result.planName(),
                    result.overageAmount(),
                    result.amount(),
                    result.status()
            );
        }
    }
}
