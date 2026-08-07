package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.CompanyTokenPlan;
import com.module06.backend.metering.domain.repository.CompanyTokenPlanRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CompanyTokenPlanPersistenceAdapter implements CompanyTokenPlanRepository {

    private final SpringDataCompanyTokenPlanRepository repository;

    public CompanyTokenPlanPersistenceAdapter(SpringDataCompanyTokenPlanRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CompanyTokenPlan> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).map(CompanyTokenPlanJpaEntity::toDomain);
    }
}
