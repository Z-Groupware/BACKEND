package com.module06.backend.project.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.module06.backend.project.domain.repository.TeamReferenceRepository;

import lombok.RequiredArgsConstructor;

/* comment.
    domain의 TeamReferenceRepository 계약을 JPA로 구현하는 어댑터.
*/
@Component
@RequiredArgsConstructor
public class TeamReferenceRepositoryAdapter implements TeamReferenceRepository {

    private final SpringDataTeamReferenceRepository springDataTeamReferenceRepository;

    @Override
    public List<Long> findExistingTeamIds(List<Long> teamIds, Long companyId) {
        return springDataTeamReferenceRepository.findAllByIdInAndCompanyId(teamIds, companyId).stream()
                .map(TeamReferenceEntity::getId)
                .toList();
    }

    @Override
    public List<TeamName> findTeamNames(List<Long> teamIds, Long companyId) {
        if (teamIds.isEmpty()) {
            return List.of();
        }
        return springDataTeamReferenceRepository.findAllByIdInAndCompanyId(teamIds, companyId).stream()
                .map(entity -> new TeamName(entity.getId(), entity.getName()))
                .toList();
    }
}
