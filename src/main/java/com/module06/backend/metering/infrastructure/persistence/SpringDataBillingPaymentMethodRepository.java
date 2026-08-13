package com.module06.backend.metering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataBillingPaymentMethodRepository extends JpaRepository<BillingPaymentMethodJpaEntity, Long> {

    Optional<BillingPaymentMethodJpaEntity> findByCompanyId(Long companyId);
}
