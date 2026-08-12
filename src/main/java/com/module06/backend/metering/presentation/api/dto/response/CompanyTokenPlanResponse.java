package com.module06.backend.metering.presentation.api.dto.response;

import com.module06.backend.metering.application.result.CompanyTokenPlanResult;

import java.time.LocalDate;

public record CompanyTokenPlanResponse(
        Long companyId,
        String planCode,
        long monthlyTokenPool,
        int baseFee,
        int tokenOveragePricePer1k,
        int inputTokenPricePer1k,
        int outputTokenPricePer1k,
        LocalDate effectiveFrom
) {

    public static CompanyTokenPlanResponse from(CompanyTokenPlanResult result) {
        return new CompanyTokenPlanResponse(
                result.companyId(),
                result.planCode(),
                result.monthlyTokenPool(),
                result.baseFee(),
                result.tokenOveragePricePer1k(),
                result.inputTokenPricePer1k(),
                result.outputTokenPricePer1k(),
                result.effectiveFrom()
        );
    }
}
