package com.module06.backend.identity.team.application.service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.team.application.command.CreateTeamCommand;
import com.module06.backend.identity.team.application.command.RenameTeamCommand;
import com.module06.backend.identity.team.application.dto.TeamNode;
import com.module06.backend.identity.team.application.port.out.TeamMemberQueryPort;
import com.module06.backend.identity.team.application.port.out.TeamMemberQueryPort.TeamMemberSummary;
import com.module06.backend.identity.team.application.port.out.TeamProjectQueryPort;
import com.module06.backend.identity.team.application.usecase.CreateTeamUseCase;
import com.module06.backend.identity.team.application.usecase.DeleteTeamUseCase;
import com.module06.backend.identity.team.application.usecase.GetTeamTreeUseCase;
import com.module06.backend.identity.team.application.usecase.RenameTeamUseCase;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class TeamService implements GetTeamTreeUseCase, CreateTeamUseCase, RenameTeamUseCase, DeleteTeamUseCase {

    private final TeamRepository teamRepository;
    private final TeamMemberQueryPort memberQueryPort;
    private final TeamProjectQueryPort projectQueryPort;

    /**
     * 부서는 계층이 없는 평평한 목록이다(2026-08-07 결정). "개발팀 안에 프론트·백엔드가 있다"는
     * team 계층이 아니라 role(구 sub_team, V2.3.4)이 담당한다 — team끼리는 부모-자식이 없다.
     */
    @Override
    @Transactional(readOnly = true)
    public List<TeamNode> getTree(Long companyId) {
        List<Team> teams = teamRepository.findByCompanyId(companyId);
        Context context = buildContext(companyId);

        return teams.stream()
                .sorted(Comparator.comparing(Team::id))
                .map(t -> toNode(t, context))
                .toList();
    }

    @Override
    @Transactional
    public TeamNode create(CreateTeamCommand command) {
        if (teamRepository.existsByCompanyIdAndName(command.companyId(), command.name())) {
            throw new BusinessException(AuthErrorCode.TEAM_NAME_DUPLICATED);
        }

        Team created = teamRepository.create(command.companyId(), command.name());
        return new TeamNode(created.id(), created.name(), null, null, 0L);
    }

    /**
     * 이름을 바꾼 뒤 실제 상태(리더·구성원 수)를 다시 조립해 응답한다 — 고정된 null/0을
     * 돌려주면 낙관적 갱신을 하는 프론트가 리더·구성원 수를 순간적으로 지운 것처럼 보이게 된다.
     */
    @Override
    @Transactional
    public TeamNode rename(RenameTeamCommand command) {
        Team team = teamRepository.findByIdAndCompanyId(command.teamId(), command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));

        boolean renamingToOtherName = !command.name().equals(team.name());
        if (renamingToOtherName && teamRepository.existsByCompanyIdAndName(command.companyId(), command.name())) {
            throw new BusinessException(AuthErrorCode.TEAM_NAME_DUPLICATED);
        }

        teamRepository.rename(team.id(), command.name());

        Team renamed = new Team(team.id(), team.companyId(), command.name(), team.leaderMemberId());
        Context context = buildContext(command.companyId());
        return toNode(renamed, context);
    }

    @Override
    @Transactional
    public void delete(Long companyId, Long teamId) {
        Team team = teamRepository.findByIdAndCompanyId(teamId, companyId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));

        if (memberQueryPort.hasActiveMembers(team.id())) {
            throw new BusinessException(AuthErrorCode.TEAM_HAS_MEMBERS);
        }
        if (projectQueryPort.hasProjects(team.id())) {
            throw new BusinessException(AuthErrorCode.TEAM_HAS_PROJECTS);
        }
        teamRepository.delete(team.id());
    }

    private Context buildContext(Long companyId) {
        List<TeamMemberSummary> members = memberQueryPort.findActiveMembersByCompany(companyId);
        Map<Long, Long> memberCountByTeam = members.stream()
                .filter(m -> m.teamId() != null)
                .collect(Collectors.groupingBy(TeamMemberSummary::teamId, Collectors.counting()));
        Map<Long, String> nameByMemberId = members.stream()
                .collect(Collectors.toMap(TeamMemberSummary::memberId, TeamMemberSummary::name));
        return new Context(memberCountByTeam, nameByMemberId);
    }

    private TeamNode toNode(Team team, Context context) {
        String leaderName = team.leaderMemberId() == null ? null : context.nameByMemberId().get(team.leaderMemberId());
        long memberCount = context.memberCountByTeam().getOrDefault(team.id(), 0L);
        return new TeamNode(team.id(), team.name(), team.leaderMemberId(), leaderName, memberCount);
    }

    private record Context(
            Map<Long, Long> memberCountByTeam,
            Map<Long, String> nameByMemberId) {
    }
}
