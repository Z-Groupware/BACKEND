package com.module06.backend.identity.team.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.team.application.command.CreateTeamCommand;
import com.module06.backend.identity.team.application.command.RenameTeamCommand;
import com.module06.backend.identity.team.application.dto.TeamNode;
import com.module06.backend.identity.team.application.port.out.TeamMemberQueryPort;
import com.module06.backend.identity.team.application.port.out.TeamProjectQueryPort;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

/* 부서는 계층이 없는 평평한 목록이다(2026-08-07 결정) — 개발팀 안의 프론트·백엔드는 team이 아니라 role이 담당한다. */
@DisplayName("부서 목록 조회")
class TeamServiceTest {

    @Test
    @DisplayName("부서 목록을 조립하고 리더 이름·구성원 수를 채운다")
    void assemblesTeamsWithLeaderNameAndMemberCount() {
        FakeTeamRepository repository = new FakeTeamRepository();
        Team team = repository.create(1L, "개발팀");
        repository.setLeader(team.id(), 2L);

        FakeMemberQueryPort memberQueryPort = new FakeMemberQueryPort();
        memberQueryPort.addActiveMember(2L, team.id(), "김서준");
        memberQueryPort.addActiveMember(3L, team.id(), "박민재");

        List<TeamNode> teams = service(repository, memberQueryPort).getTree(1L);

        assertThat(teams).hasSize(1);
        TeamNode node = teams.get(0);
        assertThat(node.name()).isEqualTo("개발팀");
        assertThat(node.leaderName()).isEqualTo("김서준");
        assertThat(node.memberCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("리더가 없는 부서는 leaderName 이 null 이다")
    void leaderNameIsNullWhenNoLeaderAssigned() {
        FakeTeamRepository repository = new FakeTeamRepository();
        repository.create(1L, "본부");

        List<TeamNode> teams = service(repository, new FakeMemberQueryPort()).getTree(1L);

        assertThat(teams.get(0).leaderName()).isNull();
        assertThat(teams.get(0).memberCount()).isZero();
    }

    @Test
    @DisplayName("다른 회사의 팀은 섞이지 않는다")
    void doesNotMixOtherCompanies() {
        FakeTeamRepository repository = new FakeTeamRepository();
        repository.create(1L, "우리회사팀");
        repository.create(2L, "다른회사팀");

        List<TeamNode> teams = service(repository, new FakeMemberQueryPort()).getTree(1L);

        assertThat(teams).extracting(TeamNode::name).containsExactly("우리회사팀");
    }

    @Test
    @DisplayName("부서를 만든다")
    void createsTeam() {
        FakeTeamRepository repository = new FakeTeamRepository();

        TeamNode node = service(repository, new FakeMemberQueryPort())
                .create(new CreateTeamCommand(1L, "개발팀"));

        assertThat(node.name()).isEqualTo("개발팀");
    }

    @Test
    @DisplayName("회사 안 이름이 중복되면 거절한다")
    void rejectsDuplicateName() {
        FakeTeamRepository repository = new FakeTeamRepository();
        repository.create(1L, "개발팀");

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort())
                .create(new CreateTeamCommand(1L, "개발팀")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TEAM_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("다른 회사라면 같은 이름이어도 허용한다")
    void allowsSameNameAcrossCompanies() {
        FakeTeamRepository repository = new FakeTeamRepository();
        repository.create(1L, "개발팀");

        TeamNode node = service(repository, new FakeMemberQueryPort())
                .create(new CreateTeamCommand(2L, "개발팀"));

        assertThat(node.name()).isEqualTo("개발팀");
    }

    @Test
    @DisplayName("이름을 바꾼다")
    void renamesTeam() {
        FakeTeamRepository repository = new FakeTeamRepository();
        Team team = repository.create(1L, "본부");

        TeamNode node = service(repository, new FakeMemberQueryPort())
                .rename(new RenameTeamCommand(1L, team.id(), "새이름"));

        assertThat(node.name()).isEqualTo("새이름");
    }

    @Test
    @DisplayName("존재하지 않는 부서면 404 로 거절한다")
    void rejectsRenamingMissingTeam() {
        FakeTeamRepository repository = new FakeTeamRepository();

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort())
                .rename(new RenameTeamCommand(1L, 999L, "새이름")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("바꾸려는 이름이 다른 부서와 겹치면 거절한다")
    void rejectsRenamingToDuplicateName() {
        FakeTeamRepository repository = new FakeTeamRepository();
        Team teamA = repository.create(1L, "1팀");
        repository.create(1L, "2팀");

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort())
                .rename(new RenameTeamCommand(1L, teamA.id(), "2팀")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TEAM_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("이름을 그대로 다시 저장해도 자기 자신과는 중복 처리하지 않는다")
    void allowsRenamingToSameName() {
        FakeTeamRepository repository = new FakeTeamRepository();
        Team team = repository.create(1L, "본부");

        TeamNode node = service(repository, new FakeMemberQueryPort())
                .rename(new RenameTeamCommand(1L, team.id(), "본부"));

        assertThat(node.name()).isEqualTo("본부");
    }

    @Test
    @DisplayName("이름을 바꿔도 응답에 구성원 수·리더 이름이 실데이터로 채워진다")
    void renameReturnsRealStateNotFabricatedDefaults() {
        FakeTeamRepository repository = new FakeTeamRepository();
        Team team = repository.create(1L, "본부");
        repository.setLeader(team.id(), 2L);

        FakeMemberQueryPort memberQueryPort = new FakeMemberQueryPort();
        memberQueryPort.addActiveMember(2L, team.id(), "김서준");

        TeamNode node = service(repository, memberQueryPort)
                .rename(new RenameTeamCommand(1L, team.id(), "새이름"));

        assertThat(node.name()).isEqualTo("새이름");
        assertThat(node.leaderName()).isEqualTo("김서준");
        assertThat(node.memberCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("구성원이 없으면 삭제된다")
    void deletesEmptyTeam() {
        FakeTeamRepository repository = new FakeTeamRepository();
        Team team = repository.create(1L, "본부");

        service(repository, new FakeMemberQueryPort()).delete(1L, team.id());

        assertThat(repository.findByIdAndCompanyId(team.id(), 1L)).isEmpty();
    }

    @Test
    @DisplayName("존재하지 않는 부서면 404 로 거절한다")
    void rejectsDeletingMissingTeam() {
        FakeTeamRepository repository = new FakeTeamRepository();

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort()).delete(1L, 999L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("소속 구성원이 있으면 삭제를 거절한다")
    void rejectsDeletingTeamWithMembers() {
        FakeTeamRepository repository = new FakeTeamRepository();
        Team team = repository.create(1L, "본부");
        FakeMemberQueryPort memberQueryPort = new FakeMemberQueryPort();
        memberQueryPort.addActiveMember(2L, team.id(), "김서준");

        assertThatThrownBy(() -> service(repository, memberQueryPort).delete(1L, team.id()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TEAM_HAS_MEMBERS);
        assertThat(repository.findByIdAndCompanyId(team.id(), 1L)).isPresent();
    }

    @Test
    @DisplayName("연결된 프로젝트가 있으면 삭제를 거절한다")
    void rejectsDeletingTeamWithProjects() {
        FakeTeamRepository repository = new FakeTeamRepository();
        Team team = repository.create(1L, "본부");
        FakeProjectQueryPort projectQueryPort = new FakeProjectQueryPort();
        projectQueryPort.addProject(team.id());

        assertThatThrownBy(() -> service(repository, new FakeMemberQueryPort(), projectQueryPort)
                .delete(1L, team.id()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TEAM_HAS_PROJECTS);
        assertThat(repository.findByIdAndCompanyId(team.id(), 1L)).isPresent();
    }

    private TeamService service(TeamRepository repository, TeamMemberQueryPort memberQueryPort) {
        return service(repository, memberQueryPort, new FakeProjectQueryPort());
    }

    private TeamService service(TeamRepository repository, TeamMemberQueryPort memberQueryPort,
                                 TeamProjectQueryPort projectQueryPort) {
        return new TeamService(repository, memberQueryPort, projectQueryPort);
    }

    /* ── 테스트 더블 ──────────────────────────────────────────────────── */

    static final class FakeTeamRepository implements TeamRepository {

        private final List<Team> teams = new ArrayList<>();
        private final Map<Long, Long> leaderByTeamId = new HashMap<>();
        private long nextId = 1;

        void setLeader(Long teamId, Long leaderMemberId) {
            leaderByTeamId.put(teamId, leaderMemberId);
            teams.replaceAll(t -> t.id().equals(teamId)
                    ? new Team(t.id(), t.companyId(), t.name(), leaderMemberId)
                    : t);
        }

        @Override
        public List<Team> findByCompanyId(Long companyId) {
            return teams.stream().filter(t -> t.companyId().equals(companyId)).toList();
        }

        @Override
        public Optional<Team> findByIdAndCompanyId(Long id, Long companyId) {
            return teams.stream().filter(t -> t.id().equals(id) && t.companyId().equals(companyId)).findFirst();
        }

        @Override
        public Team create(Long companyId, String name) {
            Team team = new Team(nextId++, companyId, name, null);
            teams.add(team);
            return team;
        }

        @Override
        public void rename(Long id, String name) {
            teams.replaceAll(t -> t.id().equals(id)
                    ? new Team(t.id(), t.companyId(), name, t.leaderMemberId())
                    : t);
        }

        @Override
        public void delete(Long id) {
            teams.removeIf(t -> t.id().equals(id));
        }

        @Override
        public boolean existsByCompanyIdAndName(Long companyId, String name) {
            return teams.stream().anyMatch(t -> t.companyId().equals(companyId) && t.name().equals(name));
        }
    }

    static final class FakeMemberQueryPort implements TeamMemberQueryPort {

        private final List<TeamMemberSummary> members = new ArrayList<>();

        void addActiveMember(Long memberId, Long teamId, String name) {
            members.add(new TeamMemberSummary(memberId, teamId, name));
        }

        @Override
        public List<TeamMemberSummary> findActiveMembersByCompany(Long companyId) {
            return members;
        }

        @Override
        public boolean hasActiveMembers(Long teamId) {
            return members.stream().anyMatch(m -> teamId.equals(m.teamId()));
        }
    }

    static final class FakeProjectQueryPort implements TeamProjectQueryPort {

        private final Set<Long> teamIdsWithProjects = new HashSet<>();

        void addProject(Long teamId) {
            teamIdsWithProjects.add(teamId);
        }

        @Override
        public boolean hasProjects(Long teamId) {
            return teamIdsWithProjects.contains(teamId);
        }
    }
}
