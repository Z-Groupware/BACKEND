package com.module06.backend.metering.presentation.api.dto.response;

import com.module06.backend.metering.application.result.DepartmentUsageResult;

public record DepartmentUsageResponse(
        Long teamId,
        long usedTokens,
        long estimatedAmountKrw
) {

    public static DepartmentUsageResponse from(DepartmentUsageResult result) {
        return new DepartmentUsageResponse(result.teamId(), result.usedTokens(), result.estimatedAmountKrw());
    }
}
