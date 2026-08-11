package com.module06.backend.metering.presentation.api.dto.response;

import com.module06.backend.metering.application.result.CompanyStoragePlanResult;

public record CompanyStoragePlanResponse(Long companyId, long storageCapBytes) {

    public static CompanyStoragePlanResponse from(CompanyStoragePlanResult result) {
        return new CompanyStoragePlanResponse(result.companyId(), result.storageCapBytes());
    }
}
