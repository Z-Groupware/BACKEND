package com.module06.backend.metering.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.BillingConfigResult;
import com.module06.backend.metering.application.result.BillingOverviewResult;
import com.module06.backend.metering.application.result.BillingPaymentActionResult;
import com.module06.backend.metering.application.usecase.GetBillingConfigUseCase;
import com.module06.backend.metering.application.usecase.GetBillingOverviewUseCase;
import com.module06.backend.metering.application.usecase.ManageBillingPaymentMethodUseCase;
import com.module06.backend.metering.application.usecase.ManageBillingSubscriptionUseCase;
import com.module06.backend.metering.domain.model.BillingDefaults;
import com.module06.backend.metering.presentation.api.dto.request.RegisterPaymentMethodRequest;
import com.module06.backend.metering.presentation.api.dto.response.BillingConfigResponse;
import com.module06.backend.metering.presentation.api.dto.response.BillingOverviewResponse;

class BillingControllerTest {

    @Test
    void payReturnsApiResponseEnvelope() {
        BillingController controller = controller(
                subscriptionUseCase(BillingPaymentActionResult.failure("NO_PAYMENT_METHOD")),
                (principal, authKey, customerKey) -> null
        );

        ApiResponse<BillingPaymentActionResult> response = controller.pay(owner());

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("Billing payment processed.");
        assertThat(response.getData().isSuccess()).isFalse();
        assertThat(response.getData().failureCode()).isEqualTo("NO_PAYMENT_METHOD");
    }

    @Test
    void registerPaymentMethodReturnsApiResponseEnvelope() {
        BillingController controller = controller(
                subscriptionUseCase(BillingPaymentActionResult.success()),
                (principal, authKey, customerKey) ->
                        new BillingOverviewResult.PaymentMethodResult(3L, "MASTER", "1234", "12/29")
        );

        ApiResponse<BillingOverviewResponse.PaymentMethodResponse> response = controller.registerPaymentMethod(
                owner(),
                new RegisterPaymentMethodRequest("mock_auth_1234", "7")
        );

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("Payment method registered.");
        assertThat(response.getData().id()).isEqualTo("pm_3");
        assertThat(response.getData().last4()).isEqualTo("1234");
    }

    @Test
    void publicBillingConfigReturnsDefaultsWithoutPrincipal() {
        PublicBillingConfigController controller = new PublicBillingConfigController();

        ApiResponse<BillingConfigResponse> response = controller.getBillingConfig();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("Billing config loaded.");
        assertThat(response.getData().baseFee()).isEqualTo(BillingDefaults.BASE_FEE);
        assertThat(response.getData().includedTokens()).isEqualTo(BillingDefaults.INCLUDED_TOKENS);
        assertThat(response.getData().includedStorageGb()).isEqualTo(BillingDefaults.INCLUDED_STORAGE_GB);
        assertThat(response.getData().overagePerThousandTokens())
                .isEqualTo(BillingDefaults.OVERAGE_PER_THOUSAND_TOKENS);
        assertThat(response.getData().overagePerGbMonth()).isEqualTo(BillingDefaults.OVERAGE_PER_GB_MONTH);
        assertThat(response.getData().isVatIncluded()).isEqualTo(BillingDefaults.VAT_INCLUDED);
    }

    private BillingController controller(
            ManageBillingSubscriptionUseCase manageBillingSubscriptionUseCase,
            ManageBillingPaymentMethodUseCase manageBillingPaymentMethodUseCase
    ) {
        GetBillingConfigUseCase getBillingConfigUseCase = companyId -> new BillingConfigResult(
                BillingDefaults.BASE_FEE,
                BillingDefaults.INCLUDED_TOKENS,
                BillingDefaults.INCLUDED_STORAGE_GB,
                BillingDefaults.OVERAGE_PER_THOUSAND_TOKENS,
                BillingDefaults.OVERAGE_PER_GB_MONTH,
                BillingDefaults.VAT_INCLUDED
        );
        GetBillingOverviewUseCase getBillingOverviewUseCase = principal ->
                new BillingOverviewResult(null, null, List.of());
        return new BillingController(
                getBillingConfigUseCase,
                getBillingOverviewUseCase,
                manageBillingSubscriptionUseCase,
                manageBillingPaymentMethodUseCase
        );
    }

    private ManageBillingSubscriptionUseCase subscriptionUseCase(BillingPaymentActionResult paymentResult) {
        return new ManageBillingSubscriptionUseCase() {
            @Override
            public BillingPaymentActionResult pay(AuthPrincipal principal) {
                return paymentResult;
            }

            @Override
            public void toggleCancel(AuthPrincipal principal, boolean canceling) {
            }
        };
    }

    private AuthPrincipal owner() {
        return new AuthPrincipal(1L, 7L, "OWNER", false, null);
    }
}
