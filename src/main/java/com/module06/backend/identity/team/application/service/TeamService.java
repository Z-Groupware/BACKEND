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

    @Override
    @Transactional(readOnly = true)
    public List<TeamNode> getTree(Long companyId) {
        List<Team> teams = teamRepository.findByCompanyId(companyId);
        Context context = buildContext(companyId, teams);

        return teams.stream()
                .filter(t -> t.parentTeamId() == null)
                .sorted(Comparator.comparing(Team::id))
                .map(t -> toNode(t, context))
                .toList();
    }

    @Override
    @Transactional
    public TeamNode create(CreateTeamCommand command) {
        if (command.parentTeamId() != null) {
            Team parent = teamRepository.findByIdAndCompanyId(command.parentTeamId(), command.companyId())
                    .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));
            if (parent.parentTeamId() != null) {
                throw new BusinessException(AuthErrorCode.TEAM_DEPTH_EXCEEDED);
            }
        }

        if (isDuplicateName(command.companyId(), command.parentTeamId(), command.name())) {
            throw new BusinessException(AuthErrorCode.TEAM_NAME_DUPLICATED);
        }

        Team created = teamRepository.create(command.companyId(), command.parentTeamId(), command.name());
        return new TeamNode(created.id(), created.name(), created.parentTeamId(), null, null, 0L, List.of());
    }

    /**
     * 이름을 바꾼 뒤 실제 상태(리더·구성원 수·하위 팀)를 다시 조립해 응답한다 — 고정된 null/0/빈 목록을
     * 돌려주면 낙관적 갱신을 하는 프론트가 하위 팀·구성원 수를 순간적으로 지운 것처럼 보이게 된다.
     */
    @Override
    @Transactional
    public TeamNode rename(RenameTeamCommand command) {
        Team team = teamRepository.findByIdAndCompanyId(command.teamId(), command.companyId())
                .orElseThrow(() -> new BusinessException(AuthErrorCode.TEAM_NOT_FOUND));

        boolean renamingToOtherName = !command.name().equals(team.name());
        if (renamingToOtherName && isDuplicateName(command.companyId(), team.parentTeamId(), command.name())) {
            throw new BusinessException(AuthErrorCode.TEAM_NAME_DUPLICATED);
        }

        teamRepository.rename(team.id(), command.name());

        Team renamed = new Team(team.id(), team.companyId(), command.name(), team.parentTeamId(), team.leaderMemberId());
        List<Team> teams = teamRepository.findByCompanyId(command.companyId());
        Context context = buildContext(command.companyId(), teams);
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
        if (teamRepository.existsByParentTeamId(team.id())) {
            throw new BusinessException(AuthErrorCode.TEAM_HAS_CHILDREN);
        }
        if (projectQueryPort.hasProjects(team.id())) {
            throw new BusinessException(AuthErrorCode.TEAM_HAS_PROJECTS);
        }
        teamRepository.delete(team.id());
    }

    /**
     * 같은 부모 안에서 이름이 겹치는지 본다(§6-2 — 전역 유니크가 아니다). Task 6(rename)도
     * 이 메서드를 그대로 쓴다 — 조건이 갈리면 생성·수정이 서로 다른 기준으로 중복을 판정하게 된다.
     */
    private boolean isDuplicateName(Long companyId, Long parentTeamId, String name) {
        return parentTeamId == null
                ? teamRepository.existsByCompanyIdAndParentTeamIdIsNullAndName(companyId, name)
                : teamRepository.existsByCompanyIdAndParentTeamIdAndName(companyId, parentTeamId, name);
    }

    private Context buildContext(Long companyId, List<Team> teams) {
        List<TeamMemberSummary> members = memberQueryPort.findActiveMembersByCompany(companyId);
        Map<Long, Long> memberCountByTeam = members.stream()
                .filter(m -> m.teamId() != null)
                .collect(Collectors.groupingBy(TeamMemberSummary::teamId, Collectors.counting()));
        Map<Long, String> nameByMemberId = members.stream()
                .collect(Collectors.toMap(TeamMemberSummary::memberId, TeamMemberSummary::name));
        Map<Long, List<Team>> childrenByParent = teams.stream()
                .filter(t -> t.parentTeamId() != null)
                .collect(Collectors.groupingBy(Team::parentTeamId));
        return new Context(childrenByParent, memberCountByTeam, nameByMemberId);
    }

    private TeamNode toNode(Team team, Context context) {
        List<TeamNode> children = context.childrenByParent().getOrDefault(team.id(), List.of()).stream()
                .sorted(Comparator.comparing(Team::id))
                .map(child -> toNode(child, context))
                .toList();
        String leaderName = team.leaderMemberId() == null ? null : context.nameByMemberId().get(team.leaderMemberId());
        long memberCount = context.memberCountByTeam().getOrDefault(team.id(), 0L);
        return new TeamNode(team.id(), team.name(), team.parentTeamId(), team.leaderMemberId(),
                leaderName, memberCount, children);
    }

    private record Context(
            Map<Long, List<Team>> childrenByParent,
            Map<Long, Long> memberCountByTeam,
            Map<Long, String> nameByMemberId) {
    }
}
