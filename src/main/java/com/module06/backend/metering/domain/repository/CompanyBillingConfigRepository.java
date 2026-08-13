package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.CompanyBillingConfig;

import java.util.Optional;

public interface CompanyBillingConfigRepository {

    Optional<CompanyBillingConfig> findByCompanyId(Long companyId);

    CompanyBillingConfig save(CompanyBillingConfig config);
}
