package com.module06.backend.identity.position.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPositionRepository extends JpaRepository<PositionJpaEntity, Long> {

    List<PositionJpaEntity> findByCompanyId(Long companyId);

    Optional<PositionJpaEntity> findByIdAndCompanyId(Long id, Long companyId);

    boolean existsByCompanyIdAndName(Long companyId, String name);
}
