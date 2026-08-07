package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.result.MeteringDashboardResult;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.model.QuotaStatus;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import com.module06.backend.metering.domain.repository.TokenUsageRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MeteringDashboardServiceTest {

    private static final Long COMPANY = 1L;
    private static final LocalDateTime AUGUST_START = LocalDateTime.of(2026, 8, 1, 0, 0);
    private static final LocalDateTime SEPTEMBER_START = LocalDateTime.of(2026, 9, 1, 0, 0);

    @Mock
    private TokenUsageRecordRepository tokenUsageRecordRepository;

    @Mock
    private CompanyTokenPlanRepository companyTokenPlanRepository;

    private MeteringDashboardService service;

    @BeforeEach
    void setUp() {
        service = new MeteringDashboardService(tokenUsageRecordRepository, companyTokenPlanRepository);
    }

    @Test
    void dashboardCalculatesCompanyUsageDepartmentBreakdownAndOverageAmount() {
        when(companyTokenPlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(plan()));
        when(tokenUsageRecordRepository.sumTotalTokens(COMPANY, AUGUST_START, SEPTEMBER_START))
                .thenReturn(1_502_300L);
        when(tokenUsageRecordRepository.sumTotalTokensByDepartment(COMPANY, AUGUST_START, SEPTEMBER_START))
                .thenReturn(List.of(
                        new TokenUsageRecordRepository.DepartmentUsageAggregate(10L, 1_000L),
                        new TokenUsageRecordRepository.DepartmentUsageAggregate(20L, 1_501_300L)
                ));

        MeteringDashboardResult result = service.getCompanyDashboard(owner(), "2026-08");

        assertThat(result.usedTokens()).isEqualTo(1_502_300L);
        assertThat(result.monthlyTokenPool()).isEqualTo(1_500_000L);
        assertThat(result.overageTokens()).isEqualTo(2_300L);
        assertThat(result.estimatedAmountKrw()).isEqualTo(150_060L);
        assertThat(result.quotaStatus()).isEqualTo(QuotaStatus.OVER);
        assertThat(result.departments()).hasSize(2);
        assertThat(result.departments().get(0).teamId()).isEqualTo(10L);
        assertThat(result.departments().get(0).usedTokens()).isEqualTo(1_000L);
        assertThat(result.departments().get(0).estimatedAmountKrw()).isEqualTo(20L);
    }

    @Test
    void dashboardFailsWhenPlanIsMissing() {
        when(companyTokenPlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCompanyDashboard(owner(), "2026-08"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MeteringErrorCode.MT_PLAN_NOT_FOUND));
    }

    @Test
    void invalidPeriodThrowsBusinessException() {
        assertThatThrownBy(() -> service.getCompanyDashboard(owner(), "2026-8"))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(MeteringErrorCode.MT_PERIOD_INVALID));
    }

    private static CompanyTokenPlan plan() {
        return CompanyTokenPlan.restore(1L, COMPANY, "STANDARD", 1_500_000L, 150_000, 20,
                LocalDate.of(2026, 1, 1));
    }

    private static AuthPrincipal owner() {
        return new AuthPrincipal(1L, COMPANY, "OWNER", false, 10L);
    }
}
