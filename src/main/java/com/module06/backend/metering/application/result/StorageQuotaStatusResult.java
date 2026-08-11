package com.module06.backend.metering.application.result;

public record StorageQuotaStatusResult(
        Long companyId,
        long usedBytes,
        long storageCapBytes,
        boolean overQuota
) {
}
