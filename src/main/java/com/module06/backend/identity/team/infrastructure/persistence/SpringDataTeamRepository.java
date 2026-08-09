package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTeamRepository extends JpaRepository<TeamJpaEntity, Long> {

    List<TeamJpaEntity> findByCompanyId(Long companyId);

    Optional<TeamJpaEntity> findByIdAndCompanyId(Long id, Long companyId);

    Optional<TeamJpaEntity> findByLeaderMemberId(Long leaderMemberId);

    boolean existsByCompanyIdAndName(Long companyId, String name);
}
