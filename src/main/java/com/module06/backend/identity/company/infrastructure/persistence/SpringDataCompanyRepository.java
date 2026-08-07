package com.module06.backend.identity.company.infrastructure.persistence;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataCompanyRepository extends JpaRepository<CompanyJpaEntity, Long> {

    Optional<CompanyJpaEntity> findByCode(String code);

    boolean existsByRegistrationNo(String registrationNo);
}
