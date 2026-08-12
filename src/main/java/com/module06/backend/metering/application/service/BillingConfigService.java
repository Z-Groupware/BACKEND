package com.module06.backend.metering.application.service;

import com.module06.backend.metering.application.result.BillingConfigResult;
import com.module06.backend.metering.application.usecase.GetBillingConfigUseCase;
import com.module06.backend.metering.domain.model.BillingDefaults;
import com.module06.backend.metering.domain.model.CompanyBillingConfig;
import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.repository.CompanyBillingConfigRepository;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Transactional(readOnly = true)
public class BillingConfigService implements GetBillingConfigUseCase {

    private static final long BYTES_PER_GB = 1024L * 1024L * 1024L;

    private final CompanyTokenPlanRepository companyTokenPlanRepository;
    private final CompanyStoragePlanRepository companyStoragePlanRepository;
    private final CompanyBillingConfigRepository companyBillingConfigRepository;

    public BillingConfigService(CompanyTokenPlanRepository companyTokenPlanRepository,
                                CompanyStoragePlanRepository companyStoragePlanRepository,
                                CompanyBillingConfigRepository companyBillingConfigRepository) {
        this.companyTokenPlanRepository = companyTokenPlanRepository;
        this.companyStoragePlanRepository = companyStoragePlanRepository;
        this.companyBillingConfigRepository = companyBillingConfigRepository;
    }

    @Override
    public BillingConfigResult getBillingConfig(Long companyId) {
        Optional<CompanyTokenPlan> tokenPlan = companyTokenPlanRepository.findByCompanyId(companyId);
        Optional<CompanyStoragePlan> storagePlan = companyStoragePlanRepository.findByCompanyId(companyId);
        Optional<CompanyBillingConfig> billingConfig = companyBillingConfigRepository.findByCompanyId(companyId);

        if (tokenPlan.isEmpty() || storagePlan.isEmpty() || billingConfig.isEmpty()) {
            return defaults();
        }

        CompanyTokenPlan token = tokenPlan.orElseThrow();
        CompanyStoragePlan storage = storagePlan.orElseThrow();
        CompanyBillingConfig config = billingConfig.orElseThrow();

        return new BillingConfigResult(
                token.getBaseFee(),
                token.getMonthlyTokenPool(),
                storage.getStorageCapBytes() / BYTES_PER_GB,
                token.getTokenOveragePricePer1k(),
                config.getStorageOveragePricePerGb(),
                config.isVatIncluded());
    }

    private BillingConfigResult defaults() {
        return new BillingConfigResult(
                BillingDefaults.BASE_FEE,
                BillingDefaults.INCLUDED_TOKENS,
                BillingDefaults.INCLUDED_STORAGE_GB,
                BillingDefaults.OVERAGE_PER_THOUSAND_TOKENS,
                BillingDefaults.OVERAGE_PER_GB_MONTH,
                BillingDefaults.VAT_INCLUDED);
    }
}
