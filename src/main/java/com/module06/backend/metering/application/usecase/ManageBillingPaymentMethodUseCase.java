package com.module06.backend.metering.application.usecase;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.BillingOverviewResult;

public interface ManageBillingPaymentMethodUseCase {

    BillingOverviewResult.PaymentMethodResult register(AuthPrincipal principal, String authKey, String customerKey);
}
