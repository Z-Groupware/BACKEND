package com.module06.backend.identity.company.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.command.OnboardCompanyCommand;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.policy.PasswordGenerator;
import com.module06.backend.identity.company.domain.repository.CompanyProfileRepository;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryQueryPort;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.Plan;
import com.module06.backend.identity.member.domain.policy.SeatLimitPolicy;
import com.module06.backend.identity.member.domain.repository.RoleRepository;
import com.module06.backend.identity.position.domain.model.Position;
import com.module06.backend.identity.position.domain.repository.PositionRepository;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

@DisplayName("CompanyOnboardingCommitter (§4-1)")
class CompanyOnboardingCommitterTest {

    private static final Long COMPANY_ID = 1L;

    @Test
    @DisplayName("정상 커밋 — 팀·역할·직급을 만들고 초대를 발급하며 팀장·어드민을 세팅한다")
    void commitsOnboarding() {
        FakeCompany company = new FakeCompany(null);
        FakeTeam team = new FakeTeam();
        FakeRole role = new FakeRole();
        FakePosition position = new FakePosition();
        FakeMemberQuery query = new FakeMemberQuery();
        FakeMemberCommand command = new FakeMemberCommand();

        CompanyOnboardingCommitter.CommitResult result = committer(company, team, role, position, query, command)
                .commit(onboardCommand(
                        List.of(teamNode("t1", "개발팀", List.of(subTeam("s1", "백엔드")))),
                        List.of(position("p1", "팀장", Authority.LEADER)),
                        List.of(invite("홍길동", "dev1@company.com", "t1", "s1", "p1", true))));

        assertThat(result.teamCount()).isEqualTo(1);
        assertThat(result.subTeamCount()).isEqualTo(1);
        assertThat(result.jobPositionCount()).isEqualTo(1);
        assertThat(result.issuedAccounts()).hasSize(1);
        assertThat(result.issuedAccounts().get(0).email()).isEqualTo("dev1@company.com");
        assertThat(result.skipped()).isEmpty();

        assertThat(team.updatedLeader).containsEntry(team.createdIds.get(0), command.issuedIds.get(0));
        assertThat(command.adminUpdates).containsEntry(command.issuedIds.get(0), true);
        assertThat(company.markedOnboardedAt).isNotNull();
    }

    @Test
    @DisplayName("이미 온보딩된 기업은 재호출을 막는다")
    void rejectsAlreadyOnboarded() {
        FakeCompany company = new FakeCompany(LocalDateTime.now());

        assertThatThrownBy(() -> committer(company, new FakeTeam(), new FakeRole(), new FakePosition(),
                new FakeMemberQuery(), new FakeMemberCommand())
                .commit(onboardCommand(List.of(), List.of(), List.of())))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ALREADY_ONBOARDED);
    }

    @Test
    @DisplayName("역할이 소속 부서 밖을 가리키면 거절한다")
    void rejectsSubTeamNotInTeam() {
        assertThatThrownBy(() -> committer(new FakeCompany(null), new FakeTeam(), new FakeRole(),
                new FakePosition(), new FakeMemberQuery(), new FakeMemberCommand())
                .commit(onboardCommand(
                        List.of(teamNode("t1", "개발팀", List.of(subTeam("s1", "백엔드"))),
                                teamNode("t2", "디자인팀", List.of())),
                        List.of(position("p1", "사원", Authority.MEMBER)),
                        List.of(invite("홍길동", "dev1@company.com", "t2", "s1", "p1", false)))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.SUB_TEAM_NOT_IN_TEAM);
    }

    @Test
    @DisplayName("같은 부서에 팀장 직급을 두 명 넣으면 거절한다")
    void rejectsDuplicateTeamLeader() {
        assertThatThrownBy(() -> committer(new FakeCompany(null), new FakeTeam(), new FakeRole(),
                new FakePosition(), new FakeMemberQuery(), new FakeMemberCommand())
                .commit(onboardCommand(
                        List.of(teamNode("t1", "개발팀", List.of())),
                        List.of(position("p1", "팀장", Authority.LEADER)),
                        List.of(invite("홍길동", "a@company.com", "t1", null, "p1", false),
                                invite("김서준", "b@company.com", "t1", null, "p1", false)))))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TEAM_LEADER_DUPLICATED);
    }

    @Test
    @DisplayName("좌석 한도를 넘으면 거절한다")
    void rejectsSeatLimitExceeded() {
        FakeMemberQuery query = new FakeMemberQuery();
        query.currentSeats = 5;

        List<OnboardCompanyCommand.InviteNode> invites = List.of(
                invite("a", "a@company.com", "t1", null, "p1", false));

        assertThatThrownBy(() -> committer(new FakeCompany(null), new FakeTeam(), new FakeRole(),
                new FakePosition(), query, new FakeMemberCommand())
                .commit(onboardCommand(
                        List.of(teamNode("t1", "개발팀", List.of())),
                        List.of(position("p1", "사원", Authority.MEMBER)),
                        invites)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.MEMBER_SEAT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("요청 안에서 이메일이 겹치면 첫 줄만 발급하고 나머지는 skip한다")
    void skipsDuplicateEmailWithinRequest() {
        FakeMemberCommand command = new FakeMemberCommand();

        CompanyOnboardingCommitter.CommitResult result = committer(new FakeCompany(null), new FakeTeam(),
                new FakeRole(), new FakePosition(), new FakeMemberQuery(), command)
                .commit(onboardCommand(
                        List.of(teamNode("t1", "개발팀", List.of())),
                        List.of(position("p1", "사원", Authority.MEMBER)),
                        List.of(invite("홍길동", "dup@company.com", "t1", null, "p1", false),
                                invite("김서준", "dup@company.com", "t1", null, "p1", false))));

        assertThat(result.issuedAccounts()).hasSize(1);
        assertThat(result.skipped()).hasSize(1);
        assertThat(result.skipped().get(0).email()).isEqualTo("dup@company.com");
        assertThat(result.skipped().get(0).reason()).isEqualTo("DUPLICATE_EMAIL");
    }

    @Test
    @DisplayName("이미 재직 중인 이메일과 겹치면 skip한다 — 삽입을 시도하지 않는다")
    void skipsDuplicateEmailAgainstExistingMember() {
        FakeMemberQuery query = new FakeMemberQuery();
        query.existingEmails.add("owner@company.com");
        FakeMemberCommand command = new FakeMemberCommand();

        CompanyOnboardingCommitter.CommitResult result = committer(new FakeCompany(null), new FakeTeam(),
                new FakeRole(), new FakePosition(), query, command)
                .commit(onboardCommand(
                        List.of(teamNode("t1", "개발팀", List.of())),
                        List.of(position("p1", "사원", Authority.MEMBER)),
                        List.of(invite("홍길동", "owner@company.com", "t1", null, "p1", false))));

        assertThat(result.issuedAccounts()).isEmpty();
        assertThat(result.skipped()).hasSize(1);
        assertThat(command.issuedIds).isEmpty();
    }

    /* ── 조립 ─────────────────────────────────────────────────────────────── */

    private CompanyOnboardingCommitter committer(FakeCompany company, FakeTeam team, FakeRole role,
                                                  FakePosition position, FakeMemberQuery query,
                                                  FakeMemberCommand command) {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        return new CompanyOnboardingCommitter(company, company, team, role, position, query, command,
                new SeatLimitPolicy(), PasswordGenerator.secure(), encoder);
    }

    private OnboardCompanyCommand onboardCommand(List<OnboardCompanyCommand.TeamNode> teams,
                                                  List<OnboardCompanyCommand.JobPositionNode> positions,
                                                  List<OnboardCompanyCommand.InviteNode> invites) {
        return new OnboardCompanyCommand(COMPANY_ID, teams, positions, invites);
    }

    private OnboardCompanyCommand.TeamNode teamNode(String tempId, String name,
                                                     List<OnboardCompanyCommand.SubTeamNode> subTeams) {
        return new OnboardCompanyCommand.TeamNode(tempId, name, subTeams);
    }

    private OnboardCompanyCommand.SubTeamNode subTeam(String tempId, String name) {
        return new OnboardCompanyCommand.SubTeamNode(tempId, name);
    }

    private OnboardCompanyCommand.JobPositionNode position(String tempId, String name, Authority authority) {
        return new OnboardCompanyCommand.JobPositionNode(tempId, name, authority);
    }

    private OnboardCompanyCommand.InviteNode invite(String name, String email, String teamTempId,
                                                     String subTeamTempId, String jobPositionTempId,
                                                     boolean isAdmin) {
        return new OnboardCompanyCommand.InviteNode(name, email, teamTempId, subTeamTempId, jobPositionTempId, isAdmin);
    }

    private static final class FakeCompany implements CompanyRepository, CompanyProfileRepository {

        private LocalDateTime onboardedAt;
        private LocalDateTime markedOnboardedAt;

        FakeCompany(LocalDateTime onboardedAt) {
            this.onboardedAt = onboardedAt;
        }

        @Override
        public Optional<Company> findByCode(String code) {
            return Optional.empty();
        }

        @Override
        public Optional<Company> findById(Long id) {
            return Optional.of(new Company(id, "NOVA-7K3D", "(주)테크스타트", null, null, null, null, onboardedAt));
        }

        @Override
        public void lockForUpdate(Long companyId) {
        }

        @Override
        public boolean existsByRegistrationNoAndIdNot(String registrationNo, Long id) {
            return false;
        }

        @Override
        public void updateProfile(Long id, String name, String registrationNo, String representativeName,
                                   String address, String mainPhone) {
        }

        @Override
        public void markOnboarded(Long id, LocalDateTime now) {
            this.markedOnboardedAt = now;
            this.onboardedAt = now;
        }
    }

    private static final class FakeTeam implements TeamRepository {

        private long nextId = 100L;
        private final List<Long> createdIds = new ArrayList<>();
        private final Map<Long, Long> updatedLeader = new HashMap<>();

        @Override
        public List<Team> findByCompanyId(Long companyId) {
            return List.of();
        }

        @Override
        public Optional<Team> findByIdAndCompanyId(Long id, Long companyId) {
            return Optional.empty();
        }

        @Override
        public Team create(Long companyId, String name) {
            long id = nextId++;
            createdIds.add(id);
            return new Team(id, companyId, name, null);
        }

        @Override
        public void rename(Long id, String name) {
        }

        @Override
        public void updateLeader(Long id, Long leaderMemberId) {
            updatedLeader.put(id, leaderMemberId);
        }

        @Override
        public void delete(Long id) {
        }

        @Override
        public boolean existsByCompanyIdAndName(Long companyId, String name) {
            return false;
        }
    }

    private static final class FakeRole implements RoleRepository {

        private long nextId = 200L;

        @Override
        public Long create(Long companyId, Long teamId, String name) {
            return nextId++;
        }
    }

    private static final class FakePosition implements PositionRepository {

        private long nextId = 300L;

        @Override
        public List<Position> findByCompanyId(Long companyId) {
            return List.of();
        }

        @Override
        public Optional<Position> findByIdAndCompanyId(Long id, Long companyId) {
            return Optional.empty();
        }

        @Override
        public Position create(Long companyId, String name, Authority authority, String description) {
            return new Position(nextId++, companyId, name, authority, description);
        }

        @Override
        public void update(Long id, String name, Authority authority, String description) {
        }

        @Override
        public void delete(Long id) {
        }

        @Override
        public boolean existsByCompanyIdAndName(Long companyId, String name) {
            return false;
        }
    }

    private static final class FakeMemberQuery implements MemberDirectoryQueryPort {

        private int currentSeats = 1;
        private final Set<String> existingEmails = new HashSet<>();

        @Override
        public List<MemberRow> findActiveByCompany(Long companyId) {
            List<MemberRow> rows = new ArrayList<>();
            for (int i = 0; i < currentSeats; i++) {
                rows.add(new MemberRow((long) i, "기존" + i, "existing" + i + "@company.com", null, null,
                        null, null, null, Authority.MEMBER, false, MemberStatus.ACTIVE,
                        LocalDate.now(), null));
            }
            return rows;
        }

        @Override
        public Optional<MemberRow> findActiveById(Long companyId, Long memberId) {
            return Optional.empty();
        }

        @Override
        public boolean existsActiveEmail(Long companyId, String email) {
            return existingEmails.contains(email);
        }

        @Override
        public Optional<Plan> findActivePlan(Long companyId) {
            return Optional.empty();
        }
    }

    private static final class FakeMemberCommand implements MemberDirectoryCommandPort {

        private long nextId = 900L;
        private final List<Long> issuedIds = new ArrayList<>();
        private final Map<Long, Boolean> adminUpdates = new HashMap<>();

        @Override
        public void updateRoleAndPosition(Long memberId, Authority authority, Long positionId) {
        }

        @Override
        public void demoteToMember(Long memberId) {
        }

        @Override
        public void updateAdmin(Long memberId, boolean isAdmin) {
            adminUpdates.put(memberId, isAdmin);
        }

        @Override
        public Long issue(Long companyId, Long teamId, Long positionId, String roleLabel, String name,
                           String email, String passwordHash, Authority authority) {
            long id = nextId++;
            issuedIds.add(id);
            return id;
        }

        @Override
        public Long issueWithRole(Long companyId, Long teamId, Long positionId, Long roleId, String name,
                                   String email, String passwordHash, Authority authority) {
            long id = nextId++;
            issuedIds.add(id);
            return id;
        }
    }
}
