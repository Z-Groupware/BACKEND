package com.module06.backend.identity.team.infrastructure.persistence;

import org.springframework.stereotype.Component;

import com.module06.backend.identity.team.application.port.out.TeamProjectQueryPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TeamProjectQueryAdapter implements TeamProjectQueryPort {

    private final SpringDataTeamProjectRefRepository repository;

    @Override
    public boolean hasProjects(Long teamId) {
        return repository.existsById_TeamId(teamId);
    }
}
