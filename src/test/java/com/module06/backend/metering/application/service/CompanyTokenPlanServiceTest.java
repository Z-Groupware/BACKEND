package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.command.SetCompanyTokenPlanCommand;
import com.module06.backend.metering.application.result.CompanyTokenPlanResult;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyTokenPlanServiceTest {

    private static final Long COMPANY = 7L;
    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-07T03:00:00Z"), ZoneId.of("Asia/Seoul"));

    @Mock
    private CompanyTokenPlanRepository companyTokenPlanRepository;

    private CompanyTokenPlanService service;

    @BeforeEach
    void setUp() {
        service = new CompanyTokenPlanService(companyTokenPlanRepository, FIXED_CLOCK);
    }

    private static AuthPrincipal owner() {
        return new AuthPrincipal(1L, COMPANY, "OWNER", false, null);
    }

    private static AuthPrincipal member() {
        return new AuthPrincipal(2L, COMPANY, "MEMBER", false, 3L);
    }

    @Test
    void setPlanInsertsWhenNoneExists() {
        when(companyTokenPlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());
        when(companyTokenPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyTokenPlanResult result = service.setPlan(owner(),
                new SetCompanyTokenPlanCommand(null, 1_500_000L, 150_000, 20, null));

        ArgumentCaptor<CompanyTokenPlan> captor = ArgumentCaptor.forClass(CompanyTokenPlan.class);
        verify(companyTokenPlanRepository).save(captor.capture());
        CompanyTokenPlan saved = captor.getValue();
        assertThat(saved.getId()).isNull();                       // 신규 → INSERT
        assertThat(saved.getPlanCode()).isEqualTo("STANDARD");    // planCode 미지정 → 기본값
        assertThat(saved.getEffectiveFrom()).isEqualTo(LocalDate.of(2026, 8, 7)); // 고정 Clock(KST)
        assertThat(result.monthlyTokenPool()).isEqualTo(1_500_000L);
    }

    @Test
    void setPlanUpdatesKeepingIdWhenExists() {
        CompanyTokenPlan existing = CompanyTokenPlan.restore(99L, COMPANY, "STANDARD",
                1_000_000L, 100_000, 20, LocalDate.of(2026, 7, 1));
        when(companyTokenPlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(existing));
        when(companyTokenPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setPlan(owner(),
                new SetCompanyTokenPlanCommand("STANDARD", 2_000_000L, 180_000, 25, LocalDate.of(2026, 8, 1)));

        ArgumentCaptor<CompanyTokenPlan> captor = ArgumentCaptor.forClass(CompanyTokenPlan.class);
        verify(companyTokenPlanRepository).save(captor.capture());
        CompanyTokenPlan saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(99L);                 // 기존 id 유지 → UPDATE (UNIQUE 위반 방지)
        assertThat(saved.getMonthlyTokenPool()).isEqualTo(2_000_000L);
    }

    @Test
    void setPlanRejectedForNonOwnerNonAdmin() {
        assertThatThrownBy(() -> service.setPlan(member(),
                new SetCompanyTokenPlanCommand(null, 1_000_000L, 100_000, 20, null)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_FORBIDDEN_SCOPE);
        verify(companyTokenPlanRepository, never()).save(any());
    }

    @Test
    void setPlanRejectsInvalidNumbers() {
        assertThatThrownBy(() -> new SetCompanyTokenPlanCommand(null, 0L, 100_000, 20, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_PLAN_COMMAND_INVALID);
    }

    @Test
    void getPlanThrowsWhenNotConfigured() {
        when(companyTokenPlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlan(owner()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_PLAN_NOT_FOUND);
    }
}
