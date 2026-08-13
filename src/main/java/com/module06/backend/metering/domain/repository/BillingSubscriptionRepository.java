package com.module06.backend.metering.domain.repository;

import com.module06.backend.metering.domain.model.BillingSubscription;

import java.util.Optional;

public interface BillingSubscriptionRepository {

    Optional<BillingSubscription> findByCompanyId(Long companyId);

    BillingSubscription save(BillingSubscription subscription);
}
