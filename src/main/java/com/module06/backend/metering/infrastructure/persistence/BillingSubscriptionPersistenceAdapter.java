package com.module06.backend.metering.infrastructure.persistence;

import com.module06.backend.metering.domain.model.BillingSubscription;
import com.module06.backend.metering.domain.repository.BillingSubscriptionRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class BillingSubscriptionPersistenceAdapter implements BillingSubscriptionRepository {

    private final SpringDataBillingSubscriptionRepository repository;

    public BillingSubscriptionPersistenceAdapter(SpringDataBillingSubscriptionRepository repository) {
        this.repository = repository;
    }

    @Override
    public Optional<BillingSubscription> findByCompanyId(Long companyId) {
        return repository.findByCompanyId(companyId).map(BillingSubscriptionJpaEntity::toDomain);
    }

    @Override
    public BillingSubscription save(BillingSubscription subscription) {
        return repository.save(BillingSubscriptionJpaEntity.from(subscription)).toDomain();
    }
}
