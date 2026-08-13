package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.BillingPaymentMethod;
import com.module06.backend.metering.domain.repository.BillingPaymentMethodRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class BillingPaymentMethodPersistenceAdapter implements BillingPaymentMethodRepository {

    private final SpringDataBillingPaymentMethodRepository repository;

    public BillingPaymentMethodPersistenceAdapter(SpringDataBillingPaymentMethodRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BillingPaymentMethod> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).map(BillingPaymentMethodJpaEntity::toDomain);
    }

    @Override
    public BillingPaymentMethod save(BillingPaymentMethod paymentMethod) {
        return repository.saveAndFlush(BillingPaymentMethodJpaEntity.from(paymentMethod)).toDomain();
    }
}
