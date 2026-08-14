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
import com.module06.backend.identity.member.application.command.DeleteMemberCommand;
import com.module06.backend.identity.member.application.command.IssueMemberCommand;
import com.module06.backend.identity.member.application.command.UpdateMemberAdminCommand;
import com.module06.backend.identity.member.application.command.UpdateMemberRoleCommand;
import com.module06.backend.identity.member.application.dto.MemberDetail;
import com.module06.backend.identity.member.application.dto.MemberListFilter;
import com.module06.backend.identity.member.application.dto.MemberPage;
import com.module06.backend.identity.member.application.dto.OrgChartTeam;
import com.module06.backend.identity.member.application.dto.TeamRosterMember;
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

        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(1);
        assertThat(page.hasNext()).isFalse();
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
    @DisplayName("이메일로도 검색한다 — 동명이인은 이메일로만 특정된다")
    void searchesByEmail() {
        FakeDirectory directory = new FakeDirectory();
        directory.addActiveWithEmail(COMPANY_ID, "김서준", "seojun@company.kr", null, null, Authority.MEMBER);
        directory.addActiveWithEmail(COMPANY_ID, "김서준", "seojun.kim@company.kr", null, null, Authority.MEMBER);
        directory.addActiveWithEmail(COMPANY_ID, "박민재", "minjae@company.kr", null, null, Authority.MEMBER);

        MemberPage page = service(directory).getMembers(COMPANY_ID, MemberListFilter.ALL, "seojun.kim", 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.content()).extracting(m -> m.name()).containsExactly("김서준");
    }

    @Test
    @DisplayName("이메일 검색도 대소문자를 가리지 않는다")
    void searchesByEmailIgnoringCase() {
        FakeDirectory directory = new FakeDirectory();
        directory.addActiveWithEmail(COMPANY_ID, "김서준", "Seojun@Company.kr", null, null, Authority.MEMBER);

        MemberPage page = service(directory).getMembers(COMPANY_ID, MemberListFilter.ALL, "SEOJUN@company", 0, 20);

        assertThat(page.totalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("size 만큼만 잘라 돌려주고 totalElements 는 필터된 전체 수다")
    void paginates() {
        FakeDirectory directory = new FakeDirectory();
        for (int i = 0; i < 5; i++) {
            directory.addActive(COMPANY_ID, "구성원" + i, null, null);
        }

        MemberPage page = service(directory).getMembers(COMPANY_ID, MemberListFilter.ALL, null, 1, 2);

        assertThat(page.totalElements()).isEqualTo(5);
        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isTrue();
        assertThat(page.content()).hasSize(2);
    }

    @Test
    @DisplayName("마지막 페이지는 hasNext 가 false 다 — 나눠떨어지지 않아도 마찬가지다")
    void lastPageHasNoNext() {
        FakeDirectory directory = new FakeDirectory();
        for (int i = 0; i < 5; i++) {
            directory.addActive(COMPANY_ID, "구성원" + i, null, null);
        }

        MemberPage page = service(directory).getMembers(COMPANY_ID, MemberListFilter.ALL, null, 2, 2);

        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.content()).hasSize(1);
    }

    @Test
    @DisplayName("page 가 Integer.MAX_VALUE 여도 hasNext 는 false 다 — page + 1 이 음수로 돌지 않는다")
    void outOfRangePageHasNoNext() {
        FakeDirectory directory = new FakeDirectory();
        directory.addActive(COMPANY_ID, "김서준", null, null);

        MemberPage page = service(directory).getMembers(
                COMPANY_ID, MemberListFilter.ALL, null, Integer.MAX_VALUE, 20);

        assertThat(page.hasNext()).isFalse();
        assertThat(page.content()).isEmpty();
    }

    @Test
    @DisplayName("결과가 없으면 totalPages 0 · hasNext false 다")
    void emptyResultHasNoPages() {
        MemberPage page = service(new FakeDirectory()).getMembers(COMPANY_ID, MemberListFilter.ALL, null, 0, 20);

        assertThat(page.totalElements()).isZero();
        assertThat(page.totalPages()).isZero();
        assertThat(page.hasNext()).isFalse();
        assertThat(page.content()).isEmpty();
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
    @DisplayName("내 팀 로스터는 같은 팀 재직자만 담는다 — 다른 팀·다른 회사는 빠진다")
    void teamRosterContainsOnlyOwnTeam() {
        FakeDirectory directory = new FakeDirectory();
        directory.addActive(COMPANY_ID, "우리팀김서준", 2L, "개발팀");
        directory.addActive(COMPANY_ID, "우리팀박민수", 2L, "개발팀");
        directory.addActive(COMPANY_ID, "옆팀이하윤", 3L, "디자인팀");
        directory.addActive(COMPANY_ID, "팀없는오너", null, null);
        directory.addActive(2L, "타사구성원", 2L, "개발팀");

        List<TeamRosterMember> roster = service(directory).getTeamRoster(COMPANY_ID, 2L);

        assertThat(roster).extracting(TeamRosterMember::name)
                .containsExactly("우리팀김서준", "우리팀박민수");
    }

    /*
     * 회의에 부를 수 없는 사람은 픽커에 뜨면 안 된다. 휴직자와 대기자(휴직·오프보딩 승인 대기)는
     * 목록에서 뺀다 — 재직자 스냅샷에는 둘 다 들어 있으므로 상태로 한 번 더 거른다.
     */
    @Test
    @DisplayName("내 팀 로스터에서 휴직자와 대기자는 빠진다")
    void teamRosterExcludesOnLeaveAndPending() {
        FakeDirectory directory = new FakeDirectory();
        directory.addActive(COMPANY_ID, "재직자", 2L, "개발팀");
        directory.addOnLeave(COMPANY_ID, "휴직자", 2L, "개발팀", Authority.MEMBER,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        directory.addWaitingInTeam(COMPANY_ID, "휴직대기자", 2L, PendingHandoverType.VACATION);
        directory.addWaitingInTeam(COMPANY_ID, "퇴사대기자", 2L, PendingHandoverType.OFFBOARDING);

        List<TeamRosterMember> roster = service(directory).getTeamRoster(COMPANY_ID, 2L);

        assertThat(roster).extracting(TeamRosterMember::name).containsExactly("재직자");
    }

    /* 팀 미배정(온보딩 전 오너)은 오류가 아니다 — 회의를 못 여는 것뿐이라 빈 목록으로 답한다. */
    @Test
    @DisplayName("teamId 가 없으면 빈 목록이다 — 예외가 아니다")
    void teamRosterWithoutTeamIsEmpty() {
        FakeDirectory directory = new FakeDirectory();
        directory.addActive(COMPANY_ID, "김서준", 2L, "개발팀");
        directory.addActive(COMPANY_ID, "팀없는오너", null, null);

        assertThat(service(directory).getTeamRoster(COMPANY_ID, null)).isEmpty();
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
    @DisplayName("역할 라벨을 안 보내면 기존 라벨을 그대로 둔다 — 직급만 고치는 요청이 라벨을 지우면 안 된다")
    void keepsRoleLabelWhenNotSent() {
        FakeDirectory directory = new FakeDirectory();
        FakePositionRepository positions = new FakePositionRepository();
        Long targetId = directory.addActiveWithRoleLabel(COMPANY_ID, "대상", null, null, "백엔드");
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");

        MemberDetail detail = service(directory, new FakeTeamRepository(), positions).update(
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, targetId, Authority.MEMBER, position.id(), null));

        assertThat(detail.roleLabel()).isEqualTo("백엔드");
    }

    @Test
    @DisplayName("역할 라벨을 보내면 그 역할로 바뀐다")
    void changesRoleLabel() {
        FakeDirectory directory = new FakeDirectory();
        FakePositionRepository positions = new FakePositionRepository();
        Long targetId = directory.addActiveWithRoleLabel(COMPANY_ID, "대상", null, null, "백엔드");
        directory.addRole(COMPANY_ID, "프론트엔드", 7L);
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");

        MemberDetail detail = service(directory, new FakeTeamRepository(), positions).update(
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, targetId, Authority.MEMBER, position.id(), "프론트엔드"));

        assertThat(detail.roleLabel()).isEqualTo("프론트엔드");
    }

    @Test
    @DisplayName("\"없음\"은 유효한 값이다 — 역할을 비우는 유일한 방법이라 회사 카탈로그에 없어도 통과한다")
    void acceptsNoneRoleLabel() {
        FakeDirectory directory = new FakeDirectory();
        FakePositionRepository positions = new FakePositionRepository();
        Long targetId = directory.addActiveWithRoleLabel(COMPANY_ID, "대상", null, null, "백엔드");
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");

        MemberDetail detail = service(directory, new FakeTeamRepository(), positions).update(
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, targetId, Authority.MEMBER, position.id(), "없음"));

        assertThat(detail.roleLabel()).isEqualTo("없음");
    }

    @Test
    @DisplayName("회사에 없는 역할 이름이면 404 — 조용히 '없음'으로 접으면 고른 역할이 사라진 채 성공한다")
    void rejectsUnknownRoleLabel() {
        FakeDirectory directory = new FakeDirectory();
        FakePositionRepository positions = new FakePositionRepository();
        Long targetId = directory.addActiveWithRoleLabel(COMPANY_ID, "대상", null, null, "백엔드");
        Position position = positions.create(COMPANY_ID, "사원", Authority.MEMBER, "설명");

        assertThatThrownBy(() -> service(directory, new FakeTeamRepository(), positions).update(
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, targetId, Authority.MEMBER, position.id(), "없는역할")))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_ROLE_LABEL_NOT_FOUND);
    }

    @Test
    @DisplayName("사원을 삭제하면 목록에서 빠지고 refresh 가 폐기된다 — 행은 남는다")
    void softDeleteRemovesMemberFromListingAndRevokesTokens() {
        FakeDirectory directory = new FakeDirectory();
        Long targetId = directory.addActive(COMPANY_ID, "퇴장", null, null);
        directory.addActive(COMPANY_ID, "잔류", null, null);
        FakeRefreshTokenStore tokenStore = new FakeRefreshTokenStore();

        service(directory, new FakeTeamRepository(), new FakePositionRepository(), tokenStore)
                .delete(new DeleteMemberCommand(COMPANY_ID, 999L, targetId));

        assertThat(directory.getMemberStatus(targetId)).isEqualTo(MemberStatus.DELETED);
        assertThat(directory.findActiveById(COMPANY_ID, targetId)).isEmpty();
        assertThat(service(directory).getMembers(COMPANY_ID, MemberListFilter.ALL, null, 0, 20).totalElements())
                .isEqualTo(1);
        assertThat(tokenStore.revokedMemberIds).contains(targetId);
    }

    @Test
    @DisplayName("삭제하면 팀장 자리가 비워진다 — 안 비우면 후임 승급이 '이미 팀장이 있다'로 막힌다")
    void softDeleteClearsTeamLeaderSeat() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        Team team = teams.create(COMPANY_ID, "개발팀");
        Long leaderId = directory.addActiveWithAuthority(COMPANY_ID, "리더", team.id(), "개발팀", Authority.LEADER);
        teams.setLeader(team.id(), leaderId);

        service(directory, teams, new FakePositionRepository())
                .delete(new DeleteMemberCommand(COMPANY_ID, 999L, leaderId));

        assertThat(teams.findByIdAndCompanyId(team.id(), COMPANY_ID).orElseThrow().leaderMemberId()).isNull();
    }

    @Test
    @DisplayName("오너는 삭제할 수 없다 — 소유자 이관은 별도 절차다")
    void rejectsOwnerDeletion() {
        FakeDirectory directory = new FakeDirectory();
        Long ownerId = directory.addActiveWithAuthority(COMPANY_ID, "오너", null, null, Authority.OWNER);

        assertThatThrownBy(() -> service(directory).delete(new DeleteMemberCommand(COMPANY_ID, 999L, ownerId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_CANNOT_MODIFY_OWNER);
    }

    @Test
    @DisplayName("본인은 삭제할 수 없다 — 마지막 관리자가 자기 계정을 닫으면 회사가 잠긴다")
    void rejectsSelfDeletion() {
        FakeDirectory directory = new FakeDirectory();
        Long selfId = directory.addActive(COMPANY_ID, "본인", null, null);

        assertThatThrownBy(() -> service(directory).delete(new DeleteMemberCommand(COMPANY_ID, selfId, selfId)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_CANNOT_MODIFY_SELF);
    }

    @Test
    @DisplayName("다른 회사 구성원은 삭제할 수 없다 — 404 다(403 은 존재를 알려준다)")
    void rejectsCrossCompanyDeletion() {
        FakeDirectory directory = new FakeDirectory();
        Long otherCompanyMemberId = directory.addActive(2L, "남의회사", null, null);

        assertThatThrownBy(() -> service(directory)
                .delete(new DeleteMemberCommand(COMPANY_ID, 999L, otherCompanyMemberId)))
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
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, targetId, Authority.OWNER, 1L, null)))
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
                new UpdateMemberRoleCommand(COMPANY_ID, selfId, selfId, Authority.LEADER, position.id(), null)))
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
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, ownerId, Authority.LEADER, position.id(), null)))
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
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, newLeaderId, Authority.LEADER, position.id(), null));

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
                new UpdateMemberRoleCommand(COMPANY_ID, 999L, leaderId, Authority.MEMBER, position.id(), null));

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

    @Test
    @DisplayName("대시보드 인원 요약 — 재직·휴직·대기를 전체로 세고, 휴직자는 VACATION 만 센다")
    void countsMembersForDashboard() {
        FakeDirectory directory = new FakeDirectory();
        directory.addActive(COMPANY_ID, "재직자", null, null);
        directory.addOnLeave(COMPANY_ID, "휴직자", null, null, Authority.MEMBER,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15));
        directory.addWaiting(COMPANY_ID, "휴직대기자", PendingHandoverType.VACATION);
        directory.addWaiting(COMPANY_ID, "퇴사대기자", PendingHandoverType.OFFBOARDING);
        directory.addActive(2L, "다른회사", null, null);

        var summary = service(directory).getDashboardSummary(COMPANY_ID);

        assertThat(summary.totalMemberCount()).isEqualTo(4);
        assertThat(summary.onLeaveMemberCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("팀장 현황은 팀마다 한 행이고, 팀 id 오름차순이다")
    void listsOneLeaderPerTeam() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        Team dev = teams.create(COMPANY_ID, "개발팀");
        Team design = teams.create(COMPANY_ID, "디자인팀");
        Long devLeader = directory.addActiveWithAuthority(COMPANY_ID, "김서준", dev.id(), "개발팀", Authority.LEADER);
        Long designLeader = directory.addOnLeave(COMPANY_ID, "강서연", design.id(), "디자인팀", Authority.LEADER,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15));
        directory.addActive(COMPANY_ID, "팀원", dev.id(), "개발팀");
        teams.setLeader(dev.id(), devLeader);
        teams.setLeader(design.id(), designLeader);

        var leaders = service(directory, teams, new FakePositionRepository()).getTeamLeadersStatus(COMPANY_ID);

        assertThat(leaders).extracting(l -> l.name()).containsExactly("김서준", "강서연");
        assertThat(leaders.get(0).teamName()).isEqualTo("개발팀");
        assertThat(leaders.get(0).status()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(leaders.get(0).leaveStartDate()).isNull();
        assertThat(leaders.get(0).leaveEndDate()).isNull();
        assertThat(leaders.get(1).status()).isEqualTo(MemberStatus.VACATION);
        assertThat(leaders.get(1).leaveStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(leaders.get(1).leaveEndDate()).isEqualTo(LocalDate.of(2026, 8, 15));
        assertThat(leaders.get(1).email()).isEqualTo("강서연@company.kr");
    }

    @Test
    @DisplayName("리더가 공석이거나 이미 빠진 팀은 행 자체가 없다")
    void skipsTeamsWithoutLeader() {
        FakeDirectory directory = new FakeDirectory();
        FakeTeamRepository teams = new FakeTeamRepository();
        Team vacant = teams.create(COMPANY_ID, "공석팀");
        Team stale = teams.create(COMPANY_ID, "퇴사팀");
        teams.setLeader(stale.id(), 999L);

        var leaders = service(directory, teams, new FakePositionRepository()).getTeamLeadersStatus(COMPANY_ID);

        assertThat(leaders).isEmpty();
        assertThat(vacant.leaderMemberId()).isNull();
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
        /** 회사별 역할 카탈로그 — 이름→id. 실제 어댑터의 role 테이블 조회를 대신한다. */
        private final Map<Long, Map<String, Long>> rolesByCompany = new HashMap<>();
        private final List<Long> softDeletedIds = new ArrayList<>();
        private long nextId = 1;

        MemberStatus getMemberStatus(Long memberId) {
            return rows.get(memberId).status;
        }

        void addRole(Long companyId, String label, Long roleId) {
            rolesByCompany.computeIfAbsent(companyId, key -> new HashMap<>()).put(label, roleId);
        }

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

        Long addOnLeave(Long companyId, String name, Long teamId, String teamName, Authority authority,
                         LocalDate leaveStartDate, LocalDate leaveEndDate) {
            long id = nextId++;
            MutableRow row = new MutableRow(id, companyId, name, name + "@company.kr", teamId, teamName, null,
                    authority, false, MemberStatus.VACATION, null);
            row.leaveStartDate = leaveStartDate;
            row.leaveEndDate = leaveEndDate;
            rows.put(id, row);
            return id;
        }

        Long addWaitingInTeam(Long companyId, String name, Long teamId, PendingHandoverType pendingType) {
            long id = nextId++;
            MutableRow row = new MutableRow(id, companyId, name, name + "@company.kr", teamId, null, null,
                    Authority.MEMBER, false, MemberStatus.WAITING, pendingType);
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
                    .filter(r -> !softDeletedIds.contains(r.id))
                    .map(this::toRow)
                    .toList();
        }

        @Override
        public Optional<MemberRow> findActiveById(Long companyId, Long memberId) {
            MutableRow row = rows.get(memberId);
            if (row == null || !row.companyId.equals(companyId) || softDeletedIds.contains(memberId)) {
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

        /** 실제 어댑터와 같이 "없음"은 회사 카탈로그를 보지 않고 시스템 역할 id 로 답한다. */
        @Override
        public Optional<Long> findRoleIdByLabel(Long companyId, String label) {
            if ("없음".equals(label)) {
                return Optional.of(2L);
            }
            return Optional.ofNullable(rolesByCompany.getOrDefault(companyId, Map.of()).get(label));
        }

        @Override
        public void updateRoleAndPosition(Long memberId, Authority authority, Long positionId, Long roleId) {
            MutableRow row = rows.get(memberId);
            row.authority = authority;
            if (roleId != null) {
                row.roleLabel = labelOf(companyOf(memberId), roleId);
            }
        }

        private Long companyOf(Long memberId) {
            return rows.get(memberId).companyId;
        }

        /** id→이름 역방향. 서비스가 넘긴 roleId 가 실제로 라벨까지 반영되는지 보려고 둔다. */
        private String labelOf(Long companyId, Long roleId) {
            if (roleId == 2L) {
                return "없음";
            }
            return rolesByCompany.getOrDefault(companyId, Map.of()).entrySet().stream()
                    .filter(entry -> entry.getValue().equals(roleId))
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElse(null);
        }

        /** 실제 어댑터처럼 행을 지우지 않는다 — 상태만 DELETED 로 바꾸고 조회에서 뺀다. */
        @Override
        public void softDelete(Long memberId) {
            rows.get(memberId).status = MemberStatus.DELETED;
            softDeletedIds.add(memberId);
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
                    row.roleLabel, row.authority, row.isAdmin, row.status, LocalDate.of(2026, 1, 1),
                    row.pendingType, row.leaveStartDate, row.leaveEndDate);
        }

        private static final class MutableRow {
            final Long id;
            final Long companyId;
            final String name;
            final String email;
            final Long teamId;
            final String teamName;
            String roleLabel;
            Authority authority;
            boolean isAdmin;
            MemberStatus status;
            final PendingHandoverType pendingType;
            /* VACATION 일 때만 어댑터가 채운다 — 그 규칙은 어댑터 쪽이라 여기서는 그냥 담아만 둔다. */
            LocalDate leaveStartDate;
            LocalDate leaveEndDate;

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
            return Optional.of(new Company(id, code, "테스트기업", null, null, null, null, null, null, null));
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

        /** 계정 발급은 이 경로를 쓰지 않는다. 쓰면 이 구현 때문에 테스트가 깨져서 드러난다. */
        @Override
        public boolean sendPasswordReset(String toEmail, String companyCode, String password) {
            throw new UnsupportedOperationException("계정 발급은 재발급 메일을 쓰지 않는다");
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
