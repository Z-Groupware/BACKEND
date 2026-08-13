package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.CompanyBillingConfig;
import com.module06.backend.metering.domain.repository.CompanyBillingConfigRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class CompanyBillingConfigPersistenceAdapter implements CompanyBillingConfigRepository {

    private final SpringDataCompanyBillingConfigRepository repository;

    public CompanyBillingConfigPersistenceAdapter(SpringDataCompanyBillingConfigRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<CompanyBillingConfig> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).map(CompanyBillingConfigJpaEntity::toDomain);
    }

    @Override
    public CompanyBillingConfig save(CompanyBillingConfig config) {
        return repository.saveAndFlush(CompanyBillingConfigJpaEntity.from(config)).toDomain();
    }
}
