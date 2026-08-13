package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.metering.domain.model.BillingDefaults;
import com.module06.backend.metering.presentation.api.dto.response.BillingConfigResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicBillingConfigController {

    // 비로그인 공개 엔드포인트다. SecurityConfig 의 경로 기반 permitAll 과 별개로,
    // AUTHZ_001(모든 매핑에 @PreAuthorize 필수) 규칙을 만족시키기 위해 익명 허용을 명시한다.
    @PreAuthorize("permitAll()")
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
