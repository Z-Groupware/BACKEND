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

        return new BillingConfigResult(
                tokenPlan.map(CompanyTokenPlan::getBaseFee).orElse(BillingDefaults.BASE_FEE),
                tokenPlan.map(CompanyTokenPlan::getMonthlyTokenPool).orElse(BillingDefaults.INCLUDED_TOKENS),
                storagePlan.map(plan -> plan.getStorageCapBytes() / BYTES_PER_GB)
                        .orElse(BillingDefaults.INCLUDED_STORAGE_GB),
                tokenPlan.map(CompanyTokenPlan::getTokenOveragePricePer1k)
                        .orElse(BillingDefaults.OVERAGE_PER_THOUSAND_TOKENS),
                billingConfig.map(CompanyBillingConfig::getStorageOveragePricePerGb)
                        .orElse(BillingDefaults.OVERAGE_PER_GB_MONTH),
                billingConfig.map(CompanyBillingConfig::isVatIncluded)
                        .orElse(BillingDefaults.VAT_INCLUDED));
    }
}
