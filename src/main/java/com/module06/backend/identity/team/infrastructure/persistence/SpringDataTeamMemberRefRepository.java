package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataTeamMemberRefRepository extends JpaRepository<TeamMemberRefEntity, Long> {

    List<TeamMemberRefEntity> findByCompanyIdAndDeletedAtIsNull(Long companyId);

    boolean existsByTeamIdAndDeletedAtIsNull(Long teamId);

    // roleId 로 이미 회사가 좁혀진다 — 호출자(TeamRoleService)가 그 역할이 이 회사·부서 것인지
    // 먼저 확인한 뒤에만 부른다. 바로 위 existsByTeamId... 와 같은 성격이다 — TENANT_001 예외
    // nosemgrep: tenant-derived-query-without-company-scope
    long countByRoleIdAndDeletedAtIsNull(Long roleId);
}
