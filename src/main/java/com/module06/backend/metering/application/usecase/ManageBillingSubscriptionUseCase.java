package com.module06.backend.metering.application.usecase;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.BillingPaymentActionResult;

public interface ManageBillingSubscriptionUseCase {

    BillingPaymentActionResult pay(AuthPrincipal principal);

    void toggleCancel(AuthPrincipal principal, boolean canceling);
}
