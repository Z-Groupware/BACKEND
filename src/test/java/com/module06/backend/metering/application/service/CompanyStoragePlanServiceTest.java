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
}
