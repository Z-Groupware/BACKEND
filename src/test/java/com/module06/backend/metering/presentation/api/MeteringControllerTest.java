package com.module06.backend.metering.presentation.api;

import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.DepartmentUsageResult;
import com.module06.backend.metering.application.result.MeteringDashboardResult;
import com.module06.backend.metering.application.result.TeamMeteringDashboardResult;
import com.module06.backend.metering.application.usecase.GetMeteringDashboardUseCase;
import com.module06.backend.metering.application.usecase.ManageCompanyStoragePlanUseCase;
import com.module06.backend.metering.application.usecase.ManageCompanyTokenPlanUseCase;
import com.module06.backend.metering.domain.model.QuotaStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.YearMonth;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MeteringController.class)
@AutoConfigureMockMvc(addFilters = false)
class MeteringControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GetMeteringDashboardUseCase getMeteringDashboardUseCase;

    @MockitoBean
    private ManageCompanyTokenPlanUseCase manageCompanyTokenPlanUseCase;

    @MockitoBean
    private ManageCompanyStoragePlanUseCase manageCompanyStoragePlanUseCase;

    @Test
    void adminDashboardExposesCompanyAmountsAndDepartmentAmounts() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(1L, 1L, "LEADER", true, 10L);
        authenticateAs(principal);
        when(getMeteringDashboardUseCase.getCompanyDashboard(eq(principal), eq("2026-08")))
                .thenReturn(new MeteringDashboardResult(
                        YearMonth.of(2026, 8),
                        1_502_300L,
                        1_500_000L,
                        2_300L,
                        150_060L,
                        QuotaStatus.OVER,
                        List.of(new DepartmentUsageResult(10L, 1_000L, 20L))
                ));

        mockMvc.perform(get("/api/metering/dashboard").param("period", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.estimatedAmountKrw").value(150_060L))
                .andExpect(jsonPath("$.data.departments[0].teamId").value(10L))
                .andExpect(jsonPath("$.data.departments[0].estimatedAmountKrw").value(20L));
    }

    @Test
    void teamDashboardDoesNotExposeAmountFields() throws Exception {
        AuthPrincipal principal = new AuthPrincipal(2L, 1L, "LEADER", false, 10L);
        authenticateAs(principal);
        when(getMeteringDashboardUseCase.getMyTeamDashboard(eq(principal), eq("2026-08")))
                .thenReturn(new TeamMeteringDashboardResult(
                        YearMonth.of(2026, 8),
                        10L,
                        1_000L,
                        1_500_000L,
                        0L,
                        QuotaStatus.WITHIN
                ));

        mockMvc.perform(get("/api/metering/dashboard/my-team").param("period", "2026-08"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teamId").value(10L))
                .andExpect(jsonPath("$.data.usedTokens").value(1_000L))
                .andExpect(jsonPath("$.data.estimatedAmountKrw").doesNotExist())
                .andExpect(jsonPath("$.data.departments").doesNotExist());
    }

    private void authenticateAs(AuthPrincipal principal) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }
}
