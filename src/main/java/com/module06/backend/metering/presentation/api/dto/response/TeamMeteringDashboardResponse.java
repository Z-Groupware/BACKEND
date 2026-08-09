package com.module06.backend.metering.presentation.api.dto.response;

import com.module06.backend.metering.application.result.TeamMeteringDashboardResult;
import com.module06.backend.metering.domain.model.QuotaStatus;

public record TeamMeteringDashboardResponse(
        String period,
        Long teamId,
        long usedTokens,
        long monthlyTokenPool,
        long overageTokens,
        QuotaStatus quotaStatus
) {

    public static TeamMeteringDashboardResponse from(TeamMeteringDashboardResult result) {
        return new TeamMeteringDashboardResponse(
                result.period().toString(),
                result.teamId(),
                result.usedTokens(),
                result.monthlyTokenPool(),
                result.overageTokens(),
                result.quotaStatus()
        );
    }
}
