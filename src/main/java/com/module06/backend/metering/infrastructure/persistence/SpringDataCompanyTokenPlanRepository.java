package com.module06.backend.metering.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SpringDataCompanyTokenPlanRepository extends JpaRepository<CompanyTokenPlanJpaEntity, Long> {

    Optional<CompanyTokenPlanJpaEntity> findByCompanyId(Long companyId);
}
