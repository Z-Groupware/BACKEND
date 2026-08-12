package com.module06.backend.metering.application.result;

import com.module06.backend.metering.domain.model.CompanyTokenPlan;

import java.time.LocalDate;

public record CompanyTokenPlanResult(
        Long companyId,
        String planCode,
        long monthlyTokenPool,
        int baseFee,
        int tokenOveragePricePer1k,
        int inputTokenPricePer1k,
        int outputTokenPricePer1k,
        LocalDate effectiveFrom
) {

    public static CompanyTokenPlanResult from(CompanyTokenPlan plan) {
        return new CompanyTokenPlanResult(
                plan.getCompanyId(),
                plan.getPlanCode(),
                plan.getMonthlyTokenPool(),
                plan.getBaseFee(),
                plan.getTokenOveragePricePer1k(),
                plan.getInputTokenPricePer1k(),
                plan.getOutputTokenPricePer1k(),
                plan.getEffectiveFrom()
        );
    }
}
