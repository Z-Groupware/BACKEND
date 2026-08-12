package com.module06.backend.metering.presentation.api.dto.response;

import com.module06.backend.metering.application.result.MeteringDashboardResult;
import com.module06.backend.metering.domain.model.QuotaStatus;

import java.util.List;

public record MeteringDashboardResponse(
        String period,
        long usedTokens,
        long monthlyTokenPool,
        long overageTokens,
        long estimatedAmountKrw,
        long directionalAmountKrw,
        QuotaStatus quotaStatus,
        List<DepartmentUsageResponse> departments
) {

    public static MeteringDashboardResponse from(MeteringDashboardResult result) {
        return new MeteringDashboardResponse(
                result.period().toString(),
                result.usedTokens(),
                result.monthlyTokenPool(),
                result.overageTokens(),
                result.estimatedAmountKrw(),
                result.directionalAmountKrw(),
                result.quotaStatus(),
                result.departments().stream()
                        .map(DepartmentUsageResponse::from)
                        .toList()
        );
    }
}
