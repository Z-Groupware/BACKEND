package com.module06.backend.metering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataBillingSubscriptionRepository extends JpaRepository<BillingSubscriptionJpaEntity, Long> {

    Optional<BillingSubscriptionJpaEntity> findByCompanyId(Long companyId);
}
