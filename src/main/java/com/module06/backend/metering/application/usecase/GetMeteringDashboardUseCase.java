package com.module06.backend.metering.application.usecase;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.MeteringDashboardResult;
import com.module06.backend.metering.application.result.TeamMeteringDashboardResult;

public interface GetMeteringDashboardUseCase {

    MeteringDashboardResult getCompanyDashboard(AuthPrincipal principal, String period);

    TeamMeteringDashboardResult getMyTeamDashboard(AuthPrincipal principal, String period);
}
