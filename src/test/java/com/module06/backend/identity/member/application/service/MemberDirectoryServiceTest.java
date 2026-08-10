package com.module06.backend.identity.member.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.company.application.port.out.AccountMailPort;
import com.module06.backend.identity.company.domain.model.Company;
import com.module06.backend.identity.company.domain.policy.PasswordGenerator;
import com.module06.backend.identity.company.domain.repository.CompanyRepository;
import com.module06.backend.identity.member.application.command.IssueMemberCommand;
import com.module06.backend.identity.member.application.command.UpdateMemberAdminCommand;
import com.module06.backend.identity.member.application.command.UpdateMemberRoleCommand;
import com.module06.backend.identity.member.application.dto.MemberDetail;
import com.module06.backend.identity.member.application.dto.MemberListFilter;
import com.module06.backend.identity.member.application.dto.MemberPage;
import com.module06.backend.identity.member.application.dto.OrgChartTeam;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryQueryPort;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.PendingHandoverType;
import com.module06.backend.identity.member.domain.model.Plan;
import com.module06.backend.identity.member.domain.policy.SeatLimitPolicy;
import com.module06.backend.identity.position.domain.model.Position;
import com.module06.backend.identity.position.domain.repository.PositionRepository;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

@DisplayName("구성원 관리(MemberDirectoryService)")
class MemberDirectoryServiceTest {

    private static final Long COMPANY_ID = 1L;

    @Test
    @DisplayName("필터 없이 조회하면 재직자 전원을 페이징해 돌려준다")
    void listsActiveMembersPaged() {
        FakeDirectory directory = new FakeDirectory();
        directory.addActive(COMPANY_ID, "김서준", null, null);
        directory.addActive(COMPANY_ID, "박민재", null, null);
        directory.addActive(2L, "다른회사", null, null);

        MemberPage page = service(directory).getMembers(COMPANY_ID, MemberListFilter.ALL, null, 0, 20);

        assertThat(page.totalCount()).isEqualTo(2);
        assertThat(page.content()).extracting(m -> m.name()).containsExactly("김서준", "박민재");
    }

    @Test
    @DisplayName("이름으로 검색한다 — roleLabel 은 검색 대상이 아니다")
    void searchesByNameOnly() {
        FakeDirectory directory = new FakeDirectory();
        directory.addActive(COMPANY_ID, "김서준", null, null);
        directory.addActive(COMPANY_ID, "박민재", null, null);

        MemberPage page = service(directory).getMembers(COMPANY_ID, MemberListFilter.ALL, "김서", 0, 20);

        assertThat(page.content()).extracting(m -> m.name()).containsExactly("김서준");
    }

    @Test
    @DisplayName("size 만큼만 잘라 돌려주고 totalCount 는 필터된 전체 수다")
    void paginates() {
        FakeDirectory directory = new FakeDirectory();
        for (int i = 0; i < 5; i++) {
            directory.addActive(COMPANY_ID, "구성원" + i, null, null);
        }

        MemberPage page = service(directory).getMembers(COMPANY_ID, MemberListFilter.ALL, null, 1, 2);

        assertThat(page.totalCount()).isEqualTo(5);
        assertThat(page.content()).hasSize(2);
    }

    @Test
    @DisplayName("LEAVE_PENDING·OFFBOARDING_PENDING 은 둘 다 WAITING 이지만 handover 대기 유형으로 구분된다")
    void distinguishesLeaveAndOffboardingPending() {
        FakeDirectory directory = new FakeDirectory();
        directory.addWaiting(COMPANY_ID, "휴직대기자", PendingHandoverType.VACATION);
        directory.addWaiting(COMPANY_ID, "퇴사대기자", PendingHandoverType.OFFBOARDING);
        directory.addActive(COMPANY_ID, "재직자", null, null);

        MemberPage leavePending = service(directory).getMembers(COMPANY_ID, MemberListFilter.LEAVE_PENDING, null, 0, 20);
        MemberPage offboardingPending = service(directory).getMembers(
                COMPANY_ID, MemberListFilter.OFFBOARDING_PENDING, null, 0, 20);

        assertThat(leavePending.content()).extracting(m -> m.name()).containsExactly("휴직대기자");
        assertThat(offboardingPending.content()).extracting(m -> m.name()).containsExactly("퇴사대기자");
    }

    @Test
    @DisplayName("조직도는 Team → SubTeam(roleLabel) → Member 3단으로 묶는다")
    void groupsOrgChartByTeamThenRoleLabel() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        Team team = teams.create(COMPANY_ID, "개발팀");
        directory.addActiveWithRoleLabel(COMPANY_ID, "김서준", team.id(), "개발팀", "백엔드");
        directory.addActiveWithRoleLabel(COMPANY_ID, "박민수", team.id(), "개발팀", "프론트엔드");
        directory.addActive(COMPANY_ID, "오너", null, null);

        List<OrgChartTeam> chart = service(directory, teams, new FakePositionRepository()).getOrgChart(COMPANY_ID);

        assertThat(chart).hasSize(1);
        assertThat(chart.get(0).subTeams()).extracting(s -> s.roleLabel()).containsExactly("백엔드", "프론트엔드");
        assertThat(chart.get(0).subTeams().get(0).members()).extracting(m -> m.name()).containsExactly("김서준");
    }

    @Test
    @DisplayName("상세 조회는 다른 회사 소속이면 못 찾는다")
    void detailScopedByCompany() {
        FakeDirectory directory = new FakeDirectory();
        Long memberId = directory.addActive(2L, "타사구성원", null, null);

        assertThatThrownBy(() -> service(directory).getDetail(COMPANY_ID, memberId))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("OWNER 로는 역할을 지정할 수 없다")
    void rejectsOwnerRoleAssignment() {
        FakeDirectory directory = new FakeDirectory();
        Long targetId = directory.addActive(COMPANY_ID, "대상", null, null);

        assertThatThrownBy(() -> service(directory).update(
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, targetId, Authority.OWNER, 1L)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_ROLE_NOT_ASSIGNABLE);
    }

    @Test
    @DisplayName("본인 정보는 변경할 수 없다")
    void rejectsSelfModification() {
        FakeDirectory directory = new FakeDirectory();
        Long selfId = directory.addActive(COMPANY_ID, "본인", null, null);
        FakePositionRepository positions = new FakePositionRepository();
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");

        assertThatThrownBy(() -> service(directory, new FakeTeamRepository(), positions).update(
                new UpdateMemberRoleCommand(COMPANY_ID, selfId, selfId, Authority.LEADER, position.id())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_CANNOT_MODIFY_SELF);
    }

    @Test
    @DisplayName("오너의 정보는 변경할 수 없다")
    void rejectsOwnerModification() {
        FakeDirectory directory = new FakeDirectory();
        Long ownerId = directory.addActiveWithAuthority(COMPANY_ID, "오너", null, null, Authority.OWNER);
        FakePositionRepository positions = new FakePositionRepository();
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");

        assertThatThrownBy(() -> service(directory, new FakeTeamRepository(), positions).update(
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, ownerId, Authority.LEADER, position.id())))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_CANNOT_MODIFY_OWNER);
    }

    @Test
    @DisplayName("LEADER 로 승격하면 기존 팀장을 MEMBER 로 내리고, 신규·기존 리더 양쪽 refresh 를 폐기한다")
    void promotingToLeaderSwapsExistingLeaderAndRevokesBothTokens() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        FakePositionRepository positions = new FakePositionRepository();
        Team team = teams.create(COMPANY_ID, "개발팀");
        Position position = positions.create(COMPANY_ID, "선임", Authority.LEADER, "설명");

        Long existingLeaderId = directory.addActiveWithAuthority(COMPANY_ID, "기존리더", team.id(), "개발팀", Authority.LEADER);
        teams.setLeader(team.id(), existingLeaderId);
        Long newLeaderId = directory.addActiveWithAuthority(COMPANY_ID, "신규리더", team.id(), "개발팀", Authority.MEMBER);

        FakeRefreshTokenStore tokenStore = new FakeRefreshTokenStore();
        MemberDetail detail = service(directory, teams, positions, tokenStore).update(
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, newLeaderId, Authority.LEADER, position.id()));

        assertThat(detail.role()).isEqualTo(Authority.LEADER);
        assertThat(teams.findByIdAndCompanyId(team.id(), COMPANY_ID).orElseThrow().leaderMemberId())
                .isEqualTo(newLeaderId);
        assertThat(directory.findActiveById(COMPANY_ID, existingLeaderId).orElseThrow().authority())
                .isEqualTo(Authority.MEMBER);
        assertThat(tokenStore.revokedMemberIds).contains(newLeaderId, existingLeaderId);
    }

    @Test
    @DisplayName("팀장을 MEMBER 로 내리면 팀의 leaderMemberId 가 비워진다")
    void demotingLeaderClearsTeamLeader() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        FakePositionRepository positions = new FakePositionRepository();
        Team team = teams.create(COMPANY_ID, "개발팀");
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");

        Long leaderId = directory.addActiveWithAuthority(COMPANY_ID, "리더", team.id(), "개발팀", Authority.LEADER);
        teams.setLeader(team.id(), leaderId);

        service(directory, teams, positions).update(
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, leaderId, Authority.MEMBER, position.id()));

        assertThat(teams.findByIdAndCompanyId(team.id(), COMPANY_ID).orElseThrow().leaderMemberId()).isNull();
    }

    @Test
    @DisplayName("오너에게는 관리 권한을 줄 수 없다")
    void rejectsAdminGrantToOwner() {
        FakeDirectory directory = new FakeDirectory();
        Long ownerId = directory.addActiveWithAuthority(COMPANY_ID, "오너", null, null, Authority.OWNER);

        assertThatThrownBy(() -> service(directory).update(new UpdateMemberAdminCommand(COMPANY_ID, ownerId, true)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_OWNER_CANNOT_BE_ADMIN);
    }

    @Test
    @DisplayName("관리 권한을 부여하면 대상자 refresh 토큰을 전부 폐기한다")
    void grantingAdminRevokesRefreshTokens() {
        FakeDirectory directory = new FakeDirectory();
        Long memberId = directory.addActiveWithAuthority(COMPANY_ID, "팀원", null, null, Authority.MEMBER);
        FakeRefreshTokenStore tokenStore = new FakeRefreshTokenStore();

        MemberDetail detail = service(directory, new FakeTeamRepository(), new FakePositionRepository(), tokenStore)
                .update(new UpdateMemberAdminCommand(COMPANY_ID, memberId, true));

        assertThat(detail.isAdmin()).isTrue();
        assertThat(tokenStore.revokedMemberIds).contains(memberId);
    }

    @Test
    @DisplayName("이미 있는 이메일로는 발급할 수 없다")
    void rejectsDuplicateEmailOnIssue() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        FakePositionRepository positions = new FakePositionRepository();
        Team team = teams.create(COMPANY_ID, "개발팀");
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");
        directory.addActiveWithEmail(COMPANY_ID, "기존", "dup@company.kr", null, null, Authority.MEMBER);

        assertThatThrownBy(() -> service(directory, teams, positions).issue(new IssueMemberCommand(
                COMPANY_ID, "홍길동", "dup@company.kr", team.id(), position.id(), Authority.MEMBER, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_EMAIL_DUPLICATED);
    }

    @Test
    @DisplayName("이미 팀장이 있는 팀에 LEADER 로 발급할 수 없다 — 잠금 아래 재검증에서도 걸린다")
    void rejectsIssueWhenTeamAlreadyHasLeader() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        FakePositionRepository positions = new FakePositionRepository();
        Team team = teams.create(COMPANY_ID, "개발팀");
        Position position = positions.create(COMPANY_ID, "선임", Authority.LEADER, "설명");
        Long existingLeaderId = directory.addActiveWithAuthority(COMPANY_ID, "기존리더", team.id(), "개발팀", Authority.LEADER);
        teams.setLeader(team.id(), existingLeaderId);

        assertThatThrownBy(() -> service(directory, teams, positions).issue(new IssueMemberCommand(
                COMPANY_ID, "홍길동", "new@company.kr", team.id(), position.id(), Authority.LEADER, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_TEAM_LEADER_ALREADY_EXISTS);
    }

    @Test
    @DisplayName("Free 요금제 좌석(5석)을 넘기면 발급할 수 없다 — 잠금 아래 재검증에서도 걸린다")
    void rejectsIssueOverSeatLimit() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        FakePositionRepository positions = new FakePositionRepository();
        Team team = teams.create(COMPANY_ID, "개발팀");
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");
        for (int i = 0; i < 5; i++) {
            directory.addActive(COMPANY_ID, "구성원" + i, null, null);
        }

        assertThatThrownBy(() -> service(directory, teams, positions).issue(new IssueMemberCommand(
                COMPANY_ID, "홍길동", "new@company.kr", team.id(), position.id(), Authority.MEMBER, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_SEAT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("발급 시 회사 행을 잠근다 — 동시 발급 직렬화의 전제조건")
    void issueLocksCompanyRow() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        FakePositionRepository positions = new FakePositionRepository();
        FakeCompanyRepository companies = new FakeCompanyRepository();
        Team team = teams.create(COMPANY_ID, "개발팀");
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");
        companies.put(COMPANY_ID, "COMP01");

        MemberDirectoryService service = new MemberDirectoryService(
                directory, directory, new MemberIssuer(directory, directory, teams, companies, new SeatLimitPolicy()),
                teams, positions, companies, new FakeAccountMailPort(), PasswordGenerator.secure(),
                new BCryptPasswordEncoder(), new FakeRefreshTokenStore(), new SeatLimitPolicy());

        service.issue(new IssueMemberCommand(
                COMPANY_ID, "홍길동", "hong@company.kr", team.id(), position.id(), Authority.MEMBER, null));

        assertThat(companies.lockedCompanyIds).containsExactly(COMPANY_ID);
    }

    @Test
    @DisplayName("정상 발급은 계정을 만들고, LEADER 면 팀장으로 지정하고, 메일을 보낸다")
    void issuesAccountSuccessfully() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        FakePositionRepository positions = new FakePositionRepository();
        FakeCompanyRepository companies = new FakeCompanyRepository();
        FakeAccountMailPort mailPort = new FakeAccountMailPort();
        Team team = teams.create(COMPANY_ID, "개발팀");
        Position position = positions.create(COMPANY_ID, "선임", Authority.LEADER, "설명");
        companies.put(COMPANY_ID, "COMP01");

        MemberDirectoryService service = new MemberDirectoryService(
                directory, directory, new MemberIssuer(directory, directory, teams, companies, new SeatLimitPolicy()),
                teams, positions, companies, mailPort, PasswordGenerator.secure(),
                new BCryptPasswordEncoder(), new FakeRefreshTokenStore(), new SeatLimitPolicy());

        var issued = service.issue(new IssueMemberCommand(
                COMPANY_ID, "홍길동", "hong@company.kr", team.id(), position.id(), Authority.LEADER, null));

        assertThat(issued.name()).isEqualTo("홍길동");
        assertThat(issued.workStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(issued.isAdmin()).isFalse();
        assertThat(teams.findByIdAndCompanyId(team.id(), COMPANY_ID).orElseThrow().leaderMemberId())
                .isEqualTo(issued.memberId());
        assertThat(mailPort.sentTo).containsExactly("hong@company.kr");
        assertThat(mailPort.sentCompanyCode).isEqualTo("COMP01");
    }

    /* ── 조립 헬퍼 ──────────────────────────────────────────────────── */

    private MemberDirectoryService service(FakeDirectory directory) {
        return service(directory, new FakeTeamRepository(), new FakePositionRepository());
    }

    private MemberDirectoryService service(FakeDirectory directory, FakeTeamRepository teams,
                                            FakePositionRepository positions) {
        return service(directory, teams, positions, new FakeRefreshTokenStore());
    }

    private MemberDirectoryService service(FakeDirectory directory, FakeTeamRepository teams,
                                            FakePositionRepository positions, RefreshTokenStore tokenStore) {
        FakeCompanyRepository companies = new FakeCompanyRepository();
        SeatLimitPolicy seatLimitPolicy = new SeatLimitPolicy();
        MemberIssuer issuer = new MemberIssuer(directory, directory, teams, companies, seatLimitPolicy);
        return new MemberDirectoryService(directory, directory, issuer, teams, positions,
                companies, new FakeAccountMailPort(), PasswordGenerator.secure(),
                new BCryptPasswordEncoder(), tokenStore, seatLimitPolicy);
    }

    /* ── 테스트 더블 ──────────────────────────────────────────────────── */

    static final class FakeDirectory implements MemberDirectoryQueryPort, MemberDirectoryCommandPort {

        private final Map<Long, MutableRow> rows = new HashMap<>();
        private final Map<Long, Plan> planByCompany = new HashMap<>();
        private final Map<Long, Long> issuedRoleIds = new HashMap<>();
        private long nextId = 1;

        Long addActive(Long companyId, String name, Long teamId, String teamName) {
            return addActiveWithAuthority(companyId, name, teamId, teamName, Authority.MEMBER);
        }

        Long addActiveWithAuthority(Long companyId, String name, Long teamId, String teamName, Authority authority) {
            return addActiveWithEmail(companyId, name, name + "@company.kr", teamId, teamName, authority);
        }

        Long addActiveWithEmail(Long companyId, String name, String email, Long teamId, String teamName,
                                 Authority authority) {
            long id = nextId++;
            MutableRow row = new MutableRow(id, companyId, name, email, teamId, teamName, null, authority,
                    false, MemberStatus.ACTIVE, null);
            rows.put(id, row);
            return id;
        }

        Long addActiveWithRoleLabel(Long companyId, String name, Long teamId, String teamName, String roleLabel) {
            long id = nextId++;
            MutableRow row = new MutableRow(id, companyId, name, name + "@company.kr", teamId, teamName, roleLabel,
                    Authority.MEMBER, false, MemberStatus.ACTIVE, null);
            rows.put(id, row);
            return id;
        }

        Long addWaiting(Long companyId, String name, PendingHandoverType pendingType) {
            long id = nextId++;
            MutableRow row = new MutableRow(id, companyId, name, name + "@company.kr", null, null, null,
                    Authority.MEMBER, false, MemberStatus.WAITING, pendingType);
            rows.put(id, row);
            return id;
        }

        @Override
        public List<MemberRow> findActiveByCompany(Long companyId) {
            return rows.values().stream()
                    .filter(r -> r.companyId.equals(companyId))
                    .map(this::toRow)
                    .toList();
        }

        @Override
        public Optional<MemberRow> findActiveById(Long companyId, Long memberId) {
            MutableRow row = rows.get(memberId);
            if (row == null || !row.companyId.equals(companyId)) {
                return Optional.empty();
            }
            return Optional.of(toRow(row));
        }

        @Override
        public boolean existsActiveEmail(Long companyId, String email) {
            return rows.values().stream().anyMatch(r -> r.companyId.equals(companyId) && r.email.equals(email));
        }

        @Override
        public Optional<Plan> findActivePlan(Long companyId) {
            return Optional.ofNullable(planByCompany.get(companyId));
        }

        @Override
        public void updateRoleAndPosition(Long memberId, Authority authority, Long positionId) {
            rows.get(memberId).authority = authority;
        }

        @Override
        public void demoteToMember(Long memberId) {
            rows.get(memberId).authority = Authority.MEMBER;
        }

        @Override
        public void updateAdmin(Long memberId, boolean isAdmin) {
            rows.get(memberId).isAdmin = isAdmin;
        }

        @Override
        public Long issue(Long companyId, Long teamId, Long positionId, String roleLabel, String name, String email,
                           String passwordHash, Authority authority) {
            long id = nextId++;
            rows.put(id, new MutableRow(id, companyId, name, email, teamId, "개발팀", roleLabel, authority, false,
                    MemberStatus.ACTIVE, null));
            return id;
        }

        @Override
        public Long issueWithRole(Long companyId, Long teamId, Long positionId, Long roleId, String name,
                                   String email, String passwordHash, Authority authority) {
            Long memberId = issue(companyId, teamId, positionId, null, name, email, passwordHash, authority);
            issuedRoleIds.put(memberId, roleId);
            return memberId;
        }

        private MemberRow toRow(MutableRow row) {
            return new MemberRow(row.id, row.name, row.email, row.teamId, row.teamName, null, "선임",
                    row.roleLabel, row.authority, row.isAdmin, row.status, LocalDate.of(2026, 1, 1), row.pendingType);
        }

        private static final class MutableRow {
            final Long id;
            final Long companyId;
            final String name;
            final String email;
            final Long teamId;
            final String teamName;
            final String roleLabel;
            Authority authority;
            boolean isAdmin;
            MemberStatus status;
            final PendingHandoverType pendingType;

            MutableRow(Long id, Long companyId, String name, String email, Long teamId, String teamName,
                       String roleLabel, Authority authority, boolean isAdmin, MemberStatus status,
                       PendingHandoverType pendingType) {
                this.id = id;
                this.companyId = companyId;
                this.name = name;
                this.email = email;
                this.teamId = teamId;
                this.teamName = teamName;
                this.roleLabel = roleLabel;
                this.authority = authority;
                this.isAdmin = isAdmin;
                this.status = status;
                this.pendingType = pendingType;
            }
        }
    }

    static final class FakeTeamRepository implements TeamRepository {

        private final List<Team> teams = new ArrayList<>();
        private long nextId = 1;

        @Override
        public List<Team> findByCompanyId(Long companyId) {
            return teams.stream().filter(t -> t.companyId().equals(companyId)).toList();
        }

        @Override
        public Optional<Team> findByIdAndCompanyId(Long id, Long companyId) {
            return teams.stream().filter(t -> t.id().equals(id) && t.companyId().equals(companyId)).findFirst();
        }

        @Override
        public Optional<Team> findByLeaderMemberId(Long leaderMemberId) {
            return teams.stream().filter(t -> leaderMemberId.equals(t.leaderMemberId())).findFirst();
        }

        @Override
        public Team create(Long companyId, String name) {
            Team team = new Team(nextId++, companyId, name, null);
            teams.add(team);
            return team;
        }

        @Override
        public void rename(Long id, String name) {
            teams.replaceAll(t -> t.id().equals(id) ? new Team(t.id(), t.companyId(), name, t.leaderMemberId()) : t);
        }

        void setLeader(Long teamId, Long leaderMemberId) {
            updateLeader(teamId, leaderMemberId);
        }

        @Override
        public void updateLeader(Long id, Long leaderMemberId) {
            teams.replaceAll(t -> t.id().equals(id)
                    ? new Team(t.id(), t.companyId(), t.name(), leaderMemberId)
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

    static final class FakePositionRepository implements PositionRepository {

        private final List<Position> positions = new ArrayList<>();
        private long nextId = 1;

        @Override
        public List<Position> findByCompanyId(Long companyId) {
            return positions.stream().filter(p -> p.companyId().equals(companyId)).toList();
        }

        @Override
        public Optional<Position> findByIdAndCompanyId(Long id, Long companyId) {
            return positions.stream().filter(p -> p.id().equals(id) && p.companyId().equals(companyId)).findFirst();
        }

        @Override
        public Position create(Long companyId, String name, Authority authority, String description) {
            Position position = new Position(nextId++, companyId, name, authority, description);
            positions.add(position);
            return position;
        }

        @Override
        public void update(Long id, String name, Authority authority, String description) {
            positions.replaceAll(p -> p.id().equals(id)
                    ? new Position(p.id(), p.companyId(), name, authority, description)
                    : p);
        }

        @Override
        public void delete(Long id) {
            positions.removeIf(p -> p.id().equals(id));
        }

        @Override
        public boolean existsByCompanyIdAndName(Long companyId, String name) {
            return positions.stream().anyMatch(p -> p.companyId().equals(companyId) && p.name().equals(name));
        }
    }

    static final class FakeCompanyRepository implements CompanyRepository {

        private final Map<Long, String> codeByCompany = new HashMap<>();
        private final List<Long> lockedCompanyIds = new ArrayList<>();

        void put(Long companyId, String code) {
            codeByCompany.put(companyId, code);
        }

        @Override
        public Optional<Company> findByCode(String code) {
            return Optional.empty();
        }

        @Override
        public Optional<Company> findById(Long id) {
            String code = codeByCompany.getOrDefault(id, "COMP00");
            return Optional.of(new Company(id, code, "테스트기업", null, null, null, null, null));
        }

        @Override
        public void lockForUpdate(Long companyId) {
            lockedCompanyIds.add(companyId);
        }
    }

    static final class FakeAccountMailPort implements AccountMailPort {

        private final List<String> sentTo = new ArrayList<>();
        private String sentCompanyCode;

        @Override
        public void sendAccountIssued(String toEmail, String companyCode, String password) {
            sentTo.add(toEmail);
            this.sentCompanyCode = companyCode;
        }
    }

    static final class FakeRefreshTokenStore implements RefreshTokenStore {

        private final List<Long> revokedMemberIds = new ArrayList<>();

        @Override
        public void save(Long memberId, String jti, java.time.Duration ttl) {
        }

        @Override
        public boolean exists(Long memberId, String jti) {
            return false;
        }

        @Override
        public void revoke(Long memberId, String jti) {
        }

        @Override
        public void revokeAllByMember(Long memberId) {
            revokedMemberIds.add(memberId);
        }
    }
}
