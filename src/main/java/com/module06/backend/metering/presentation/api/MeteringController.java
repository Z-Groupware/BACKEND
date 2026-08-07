package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.usecase.GetMeteringDashboardUseCase;
import com.module06.backend.metering.presentation.api.dto.response.MeteringDashboardResponse;
import com.module06.backend.metering.presentation.api.dto.response.TeamMeteringDashboardResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/metering")
public class MeteringController {

    private final GetMeteringDashboardUseCase getMeteringDashboardUseCase;

    public MeteringController(GetMeteringDashboardUseCase getMeteringDashboardUseCase) {
        this.getMeteringDashboardUseCase = getMeteringDashboardUseCase;
    }

    @GetMapping("/dashboard")
    public ApiResponse<MeteringDashboardResponse> dashboard(@AuthenticationPrincipal AuthPrincipal principal,
                                                            @RequestParam(required = false) String period) {
        MeteringDashboardResponse response = MeteringDashboardResponse.from(
                getMeteringDashboardUseCase.getCompanyDashboard(principal, period));
        return ApiResponse.success("Metering dashboard loaded.", response);
    }

    @GetMapping("/dashboard/my-team")
    public ApiResponse<TeamMeteringDashboardResponse> myTeamDashboard(@AuthenticationPrincipal AuthPrincipal principal,
                                                                      @RequestParam(required = false) String period) {
        TeamMeteringDashboardResponse response = TeamMeteringDashboardResponse.from(
                getMeteringDashboardUseCase.getMyTeamDashboard(principal, period));
        return ApiResponse.success("Team metering dashboard loaded.", response);
    }
}
