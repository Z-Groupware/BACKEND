package com.module06.backend.metering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataCompanyBillingConfigRepository extends JpaRepository<CompanyBillingConfigJpaEntity, Long> {

    Optional<CompanyBillingConfigJpaEntity> findByCompanyId(Long companyId);
}
