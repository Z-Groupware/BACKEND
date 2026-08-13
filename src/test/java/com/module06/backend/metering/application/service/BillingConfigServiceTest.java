package com.module06.backend.metering.application.service;

import com.module06.backend.metering.application.result.BillingConfigResult;
import com.module06.backend.metering.domain.model.BillingDefaults;
import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.repository.CompanyBillingConfigRepository;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BillingConfigServiceTest {

    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    @Mock
    private CompanyTokenPlanRepository tokenPlans;

    @Mock
    private CompanyStoragePlanRepository storagePlans;

    @Mock
    private CompanyBillingConfigRepository billingConfigs;

    private BillingConfigService service;

    @BeforeEach
    void setUp() {
        service = new BillingConfigService(tokenPlans, storagePlans, billingConfigs);
    }

    @Test
    void usesRealTokenAndStorageValuesWhenBillingConfigIsMissing() {
        when(tokenPlans.findByCompanyId(7L)).thenReturn(Optional.of(CompanyTokenPlan.restore(
                1L, 7L, "STANDARD", 2_000_000L, 180_000, 30, 30, 30,
                LocalDate.of(2026, 8, 1))));
        when(storagePlans.findByCompanyId(7L)).thenReturn(Optional.of(CompanyStoragePlan.restore(
                2L, 7L, 80L * BYTES_PER_GB)));
        when(billingConfigs.findByCompanyId(7L)).thenReturn(Optional.empty());

        BillingConfigResult result = service.getBillingConfig(7L);

        assertThat(result.baseFee()).isEqualTo(180_000);
        assertThat(result.includedTokens()).isEqualTo(2_000_000L);
        assertThat(result.includedStorageGb()).isEqualTo(80L);
        assertThat(result.overagePerThousandTokens()).isEqualTo(30);
        assertThat(result.overagePerGbMonth()).isEqualTo(BillingDefaults.OVERAGE_PER_GB_MONTH);
        assertThat(result.isVatIncluded()).isEqualTo(BillingDefaults.VAT_INCLUDED);
    }
}
