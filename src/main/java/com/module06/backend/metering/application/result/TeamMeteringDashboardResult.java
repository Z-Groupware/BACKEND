package com.module06.backend.metering.application.result;

import com.module06.backend.metering.domain.model.QuotaStatus;

import java.time.YearMonth;

public record TeamMeteringDashboardResult(
        YearMonth period,
        Long teamId,
        long usedTokens,
        long monthlyTokenPool,
        long overageTokens,
        QuotaStatus quotaStatus
) {
}
