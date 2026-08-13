package com.module06.backend.metering.application.usecase;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.BillingOverviewResult;

public interface GetBillingOverviewUseCase {

    BillingOverviewResult getBillingOverview(AuthPrincipal principal);
}
