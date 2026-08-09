package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.module06.backend.identity.team.application.port.out.TeamMemberQueryPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TeamMemberQueryAdapter implements TeamMemberQueryPort {

    private final SpringDataTeamMemberRefRepository repository;

    @Override
    public List<TeamMemberSummary> findActiveMembersByCompany(Long companyId) {
        return repository.findByCompanyIdAndDeletedAtIsNull(companyId).stream()
                .map(e -> new TeamMemberSummary(e.getId(), e.getTeamId(), e.getName()))
                .toList();
    }

    @Override
    public boolean hasActiveMembers(Long teamId) {
        return repository.existsByTeamIdAndDeletedAtIsNull(teamId);
    }
}
