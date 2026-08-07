package com.module06.backend.metering.application.result;

import com.module06.backend.metering.domain.model.QuotaStatus;

import java.time.YearMonth;
import java.util.List;

public record MeteringDashboardResult(
        YearMonth period,
        long usedTokens,
        long monthlyTokenPool,
        long overageTokens,
        long estimatedAmountKrw,
        QuotaStatus quotaStatus,
        List<DepartmentUsageResult> departments
) {
}
