package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.metering.domain.model.BillingDefaults;
import com.module06.backend.metering.presentation.api.dto.response.BillingConfigResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicBillingConfigController {

    @GetMapping("/api/billing-config")
    public ApiResponse<BillingConfigResponse> getBillingConfig() {
        BillingConfigResponse response = new BillingConfigResponse(
                BillingDefaults.BASE_FEE,
                BillingDefaults.INCLUDED_TOKENS,
                BillingDefaults.INCLUDED_STORAGE_GB,
                BillingDefaults.OVERAGE_PER_THOUSAND_TOKENS,
                BillingDefaults.OVERAGE_PER_GB_MONTH,
                BillingDefaults.VAT_INCLUDED
        );
        return ApiResponse.success("Billing config loaded.", response);
    }
}
