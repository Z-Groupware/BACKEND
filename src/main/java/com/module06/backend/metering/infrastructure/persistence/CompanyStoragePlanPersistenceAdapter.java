package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.CompanyStoragePlan;
import com.module06.backend.metering.domain.repository.CompanyStoragePlanRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CompanyStoragePlanPersistenceAdapter implements CompanyStoragePlanRepository {

    private final SpringDataCompanyStoragePlanRepository repository;

    public CompanyStoragePlanPersistenceAdapter(SpringDataCompanyStoragePlanRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CompanyStoragePlan> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).map(CompanyStoragePlanJpaEntity::toDomain);
    }

    @Override
    public CompanyStoragePlan save(CompanyStoragePlan plan) {
        return repository.save(CompanyStoragePlanJpaEntity.from(plan)).toDomain();
    }
}
