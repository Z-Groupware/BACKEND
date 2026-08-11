package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.CompanyStoragePlan;

import java.util.Optional;

public interface CompanyStoragePlanRepository {

    Optional<CompanyStoragePlan> findByCompanyId(Long companyId);

    CompanyStoragePlan save(CompanyStoragePlan plan);
}
