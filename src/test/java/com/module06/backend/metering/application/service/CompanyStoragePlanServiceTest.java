package com.module06.backend.metering.application.service;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.metering.application.command.SetCompanyStoragePlanCommand;
import com.module06.backend.metering.application.result.CompanyStoragePlanResult;
import com.module06.backend.metering.domain.exception.MeteringErrorCode;
import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyStoragePlanServiceTest {

    private static final Long COMPANY = 7L;
    // 교차 회사 격리 검증용 — COMPANY 와 다른 회사(CompanyTokenPlanServiceTest 와 같은 규약).
    private static final Long OTHER_COMPANY = 8L;

    @Mock
    private CompanyStoragePlanRepository companyStoragePlanRepository;

    private CompanyStoragePlanService service;

    @BeforeEach
    void setUp() {
        service = new CompanyStoragePlanService(companyStoragePlanRepository);
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
        when(companyStoragePlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());
        when(companyStoragePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompanyStoragePlanResult result = service.setPlan(owner(), new SetCompanyStoragePlanCommand(100_000_000L));

        ArgumentCaptor<CompanyStoragePlan> captor = ArgumentCaptor.forClass(CompanyStoragePlan.class);
        verify(companyStoragePlanRepository).save(captor.capture());
        CompanyStoragePlan saved = captor.getValue();
        assertThat(saved.getId()).isNull();                       // 신규 → INSERT
        assertThat(result.storageCapBytes()).isEqualTo(100_000_000L);
    }

    @Test
    void setPlanUpdatesKeepingIdWhenExists() {
        CompanyStoragePlan existing = CompanyStoragePlan.restore(99L, COMPANY, 50_000_000L);
        when(companyStoragePlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.of(existing));
        when(companyStoragePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setPlan(owner(), new SetCompanyStoragePlanCommand(200_000_000L));

        ArgumentCaptor<CompanyStoragePlan> captor = ArgumentCaptor.forClass(CompanyStoragePlan.class);
        verify(companyStoragePlanRepository).save(captor.capture());
        CompanyStoragePlan saved = captor.getValue();
        assertThat(saved.getId()).isEqualTo(99L);                 // 기존 id 유지 → UPDATE (UNIQUE 위반 방지)
        assertThat(saved.getStorageCapBytes()).isEqualTo(200_000_000L);
    }

    @Test
    void setPlanRejectedForNonOwnerNonAdmin() {
        assertThatThrownBy(() -> service.setPlan(member(), new SetCompanyStoragePlanCommand(100_000_000L)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_FORBIDDEN_SCOPE);
        verify(companyStoragePlanRepository, never()).save(any());
    }

    @Test
    void setPlanRejectsInvalidCap() {
        assertThatThrownBy(() -> new SetCompanyStoragePlanCommand(0L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_STORAGE_PLAN_COMMAND_INVALID);
    }

    @Test
    void getPlanThrowsWhenNotConfigured() {
        when(companyStoragePlanRepository.findByCompanyId(COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlan(owner()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_STORAGE_PLAN_NOT_FOUND);
    }

    // ── 교차 회사 격리 ────────────────────────────────────────────────────
    // 인가가 컨트롤러가 아니라 이 서비스에 있어서(MeteringController 에 @PreAuthorize 없음,
    // Gate 1 AUTHZ_001), 회사 경계도 여기서 단언하지 않으면 아무 데서도 안 본다.
    // 요청(SetCompanyStoragePlanCommand)에 companyId 필드가 없고 principal 에서만 온다는 것이
    // 이 격리의 근거다.

    @Test
    void setPlanWritesOnlyToPrincipalCompany() {
        when(companyStoragePlanRepository.findByCompanyId(OTHER_COMPANY)).thenReturn(Optional.empty());
        when(companyStoragePlanRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.setPlan(otherCompanyOwner(), new SetCompanyStoragePlanCommand(100_000_000L));

        ArgumentCaptor<CompanyStoragePlan> captor = ArgumentCaptor.forClass(CompanyStoragePlan.class);
        verify(companyStoragePlanRepository).save(captor.capture());
        assertThat(captor.getValue().getCompanyId()).isEqualTo(OTHER_COMPANY);
        verify(companyStoragePlanRepository, never()).findByCompanyId(COMPANY);
    }

    @Test
    void getPlanDoesNotLeakAnotherCompanyPlan() {
        when(companyStoragePlanRepository.findByCompanyId(OTHER_COMPANY)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getPlan(otherCompanyOwner()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", MeteringErrorCode.MT_STORAGE_PLAN_NOT_FOUND);
        verify(companyStoragePlanRepository, never()).findByCompanyId(COMPANY);
    }
}
