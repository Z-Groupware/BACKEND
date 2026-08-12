package com.module06.backend.metering.application.usecase;

import com.module06.backend.metering.application.result.BillingConfigResult;

public interface GetBillingConfigUseCase {

    BillingConfigResult getBillingConfig(Long companyId);
}
