package com.module06.backend.identity.team.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTeamProjectRefRepository extends JpaRepository<TeamProjectRefEntity, TeamProjectRefId> {

    boolean existsById_TeamId(Long teamId);
}
