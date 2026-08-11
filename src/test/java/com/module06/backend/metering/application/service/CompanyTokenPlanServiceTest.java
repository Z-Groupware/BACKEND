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
    // 교차 회사 격리 검증용 — COMPANY 와 다른 회사. 이 값으로 부른 요청이 COMPANY 의 행에
    // 닿으면 테넌트 경계가 깨진 것이다.
    private static final Long OTHER_COMPANY = 8L;
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

    private static AuthPrincipal otherCompanyOwner() {
        return new AuthPrincipal(9L, OTHER_COMPANY, "OWNER", false, null);
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

    // ── 교차 회사 격리 ────────────────────────────────────────────────────
    // 이 도메인은 인가를 컨트롤러 @PreAuthorize 가 아니라 서비스에서 한다
    // (MeteringController 에 @PreAuthorize 가 없다 — Gate 1 AUTHZ_001 이 짚은 지점).
    // 그래서 "다른 회사에 닿지 않는다"를 여기서 단언하지 않으면 아무 데서도 안 본다.
    //
    // 핵심은 companyId 의 출처다. 요청(SetCompanyTokenPlanCommand)에는 companyId 필드가
    // 아예 없고 principal 에서만 온다 — 그 불변식이 깨지면 아래 두 테스트가 먼저 깨진다.

    @Test
    void setPlanWritesOnlyToPrincipalCompany() {
        when(companyTokenPlanRepository.findByCompanyId(OTHER_COMPANY)).thenReturn(Optional.empty());
        when(companyTokenPlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setPlan(otherCompanyOwner(),
                new SetCompanyTokenPlanCommand(null, 1_500_000L, 150_000, 20, null));

        ArgumentCaptor<CompanyTokenPlan> captor = ArgumentCaptor.forClass(CompanyTokenPlan.class);
        verify(companyTokenPlanRepository).save(captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo(OTHER_COMPANY);
        // 남의 회사 행은 읽지도 쓰지도 않는다.
        verify(companyTokenPlanRepository, never()).findByCompanyId(COMPANY);
    }

    @Test
    void getPlanDoesNotLeakAnotherCompanyPlan() {
        // 저장소에는 COMPANY 의 요금제만 있고, OTHER_COMPANY 것은 없다.
        when(companyTokenPlanRepository.findByCompanyId(OTHER_COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlan(otherCompanyOwner()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_PLAN_NOT_FOUND);
        // 못 찾았다고 다른 회사로 넘어가 다시 찾지 않는다.
        verify(companyTokenPlanRepository, never()).findByCompanyId(COMPANY);
    }
}
