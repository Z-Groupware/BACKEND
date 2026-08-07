package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.CompanyTokenPlan;

import java.util.Optional;

public interface CompanyTokenPlanRepository {

    Optional<CompanyTokenPlan> findByCompanyId(Long companyId);

    CompanyTokenPlan save(CompanyTokenPlan plan);
}
