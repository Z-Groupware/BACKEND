package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.usecase.GetBillingOverviewUseCase;
import com.module06.backend.metering.application.usecase.GetBillingConfigUseCase;
import com.module06.backend.metering.presentation.api.dto.response.BillingConfigResponse;
import com.module06.backend.metering.presentation.api.dto.response.BillingOverviewResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/companies/me")
public class BillingController {

    private final GetBillingConfigUseCase getBillingConfigUseCase;
    private final GetBillingOverviewUseCase getBillingOverviewUseCase;

    public BillingController(GetBillingConfigUseCase getBillingConfigUseCase,
                             GetBillingOverviewUseCase getBillingOverviewUseCase) {
        this.getBillingConfigUseCase = getBillingConfigUseCase;
        this.getBillingOverviewUseCase = getBillingOverviewUseCase;
    }

    // 조회는 로그인 전원 허용(§0-1). 변경 계열(pay·card·cancel)은 각 엔드포인트에서 OWNER/admin으로 좁힌다.
    @PreAuthorize("isAuthenticated()")
    @GetMapping("/billing-config")
    public ApiResponse<BillingConfigResponse> getBillingConfig(@AuthenticationPrincipal AuthPrincipal principal) {
        BillingConfigResponse response = BillingConfigResponse.from(
                getBillingConfigUseCase.getBillingConfig(principal.companyId()));
        return ApiResponse.success("Billing config loaded.", response);
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping("/billing")
    public ApiResponse<BillingOverviewResponse> getBillingOverview(@AuthenticationPrincipal AuthPrincipal principal) {
        BillingOverviewResponse response = BillingOverviewResponse.from(
                getBillingOverviewUseCase.getBillingOverview(principal));
        return ApiResponse.success("Billing overview loaded.", response);
    }
}
