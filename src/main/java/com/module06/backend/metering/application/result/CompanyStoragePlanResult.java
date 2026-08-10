package com.module06.backend.metering.application.result;

import com.module06.backend.metering.domain.model.CompanyStoragePlan;

public record CompanyStoragePlanResult(Long companyId, long storageCapBytes) {

    public static CompanyStoragePlanResult from(CompanyStoragePlan plan) {
        return new CompanyStoragePlanResult(plan.getCompanyId(), plan.getStorageCapBytes());
    }
}
