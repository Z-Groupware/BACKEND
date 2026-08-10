package com.module06.backend.metering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataCompanyStoragePlanRepository extends JpaRepository<CompanyStoragePlanJpaEntity, Long> {

    Optional<CompanyStoragePlanJpaEntity> findByCompanyId(Long companyId);
}
