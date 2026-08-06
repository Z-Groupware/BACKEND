package com.module06.backend.identity.team.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.identity.team.application.dto.TeamNode;
import com.module06.backend.identity.team.application.port.out.TeamMemberQueryPort;
import com.module06.backend.identity.team.application.port.out.TeamMemberQueryPort.TeamMemberSummary;
import com.module06.backend.identity.team.application.usecase.GetTeamTreeUseCase;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamService implements GetTeamTreeUseCase {

    private final TeamRepository teamRepository;
    private final TeamMemberQueryPort memberQueryPort;

    @Override
    @Transactional(readOnly = true)
    public List<TeamNode> getTree(Long companyId) {
        List<Team> teams = teamRepository.findByCompanyId(companyId);
        List<TeamMemberSummary> members = memberQueryPort.findActiveMembersByCompany(companyId);

        Map<Long, Long> memberCountByTeam = members.stream()
                .filter(m -> m.teamId() != null)
                .collect(Collectors.groupingBy(TeamMemberSummary::teamId, Collectors.counting()));
        Map<Long, String> nameByMemberId = members.stream()
                .collect(Collectors.toMap(TeamMemberSummary::memberId, TeamMemberSummary::name));
        Map<Long, List<Team>> childrenByParent = teams.stream()
                .filter(t -> t.parentTeamId() != null)
                .collect(Collectors.groupingBy(Team::parentTeamId));

        return teams.stream()
                .filter(t -> t.parentTeamId() == null)
                .sorted(Comparator.comparing(Team::id))
                .map(t -> toNode(t, childrenByParent, memberCountByTeam, nameByMemberId))
                .toList();
    }

    private TeamNode toNode(Team team, Map<Long, List<Team>> childrenByParent,
                            Map<Long, Long> memberCountByTeam, Map<Long, String> nameByMemberId) {
        List<TeamNode> children = childrenByParent.getOrDefault(team.id(), List.of()).stream()
                .sorted(Comparator.comparing(Team::id))
                .map(child -> toNode(child, childrenByParent, memberCountByTeam, nameByMemberId))
                .toList();
        String leaderName = team.leaderMemberId() == null ? null : nameByMemberId.get(team.leaderMemberId());
        long memberCount = memberCountByTeam.getOrDefault(team.id(), 0L);
        return new TeamNode(team.id(), team.name(), team.parentTeamId(), team.leaderMemberId(),
                leaderName, memberCount, children);
    }
}
