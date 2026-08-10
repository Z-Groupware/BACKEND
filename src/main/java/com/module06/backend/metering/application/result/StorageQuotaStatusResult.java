package com.module06.backend.metering.application.result;

import com.module06.backend.metering.domain.model.QuotaStatus;

public record StorageQuotaStatusResult(
        Long companyId,
        long usedBytes,
        long storageCapBytes,
        QuotaStatus quotaStatus
) {
}
