package com.module06.backend.metering.application.result;

public record DepartmentUsageResult(
        Long teamId,
        long usedTokens,
        long estimatedAmountKrw
) {
}
