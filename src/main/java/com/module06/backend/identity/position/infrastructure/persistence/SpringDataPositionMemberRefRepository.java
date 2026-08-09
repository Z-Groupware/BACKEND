package com.module06.backend.identity.position.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataPositionMemberRefRepository extends JpaRepository<PositionMemberRefEntity, Long> {

    List<PositionMemberRefEntity> findByCompanyIdAndDeletedAtIsNull(Long companyId);

    boolean existsByPositionIdAndDeletedAtIsNull(Long positionId);
}
