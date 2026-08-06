package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTeamMemberRefRepository extends JpaRepository<TeamMemberRefEntity, Long> {

    List<TeamMemberRefEntity> findByCompanyIdAndDeletedAtIsNull(Long companyId);

    boolean existsByTeamIdAndDeletedAtIsNull(Long teamId);
}
