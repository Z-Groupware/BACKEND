package com.module06.backend.identity.member.application.service;

import java.time.Duration;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.application.port.out.RefreshTokenStore;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.application.command.UpdateMyProfileCommand;
import com.module06.backend.identity.member.application.dto.MyProfile;
import com.module06.backend.identity.member.application.port.out.MemberDirectoryCommandPort;
import com.module06.backend.identity.member.application.port.out.MyProfileCommandPort;
import com.module06.backend.identity.member.application.port.out.MyProfileQueryPort;
import com.module06.backend.identity.member.domain.model.Authority;
import com.module06.backend.identity.member.domain.model.MemberStatus;
import com.module06.backend.identity.member.domain.model.Plan;
import com.module06.backend.identity.position.domain.model.Position;
import com.module06.backend.identity.position.domain.repository.PositionRepository;
import com.module06.backend.identity.team.domain.model.Team;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("MyProfileService")
class MyProfileServiceTest {

    private static final Long COMPANY_ID = 1L;
    private static final Long MEMBER_ID = 3L;

    @Test
    @DisplayName("memberId 로 프로필을 돌려준다")
    void returnsProfile() {
        MyProfileService service = service(new FakeQuery(profile()));

        assertThat(service.get(MEMBER_ID).name()).isEqualTo("이하윤");
    }

    @Test
    @DisplayName("없는 회원은 MEMBER_NOT_FOUND — 토큰은 유효한데 회원이 지워진 경우다")
    void missingMemberThrows() {
        MyProfileService service = service(new FakeQuery(null));

        assertThatThrownBy(() -> service.get(999L))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.MEMBER_NOT_FOUND);
    }

    @Test
    @DisplayName("받은 memberId 를 그대로 조회에 넘긴다 — 남의 프로필을 내려주면 정보 유출이다")
    void passesRequestedMemberIdThrough() {
        FakeQuery query = new FakeQuery(profile());
        service(query).get(42L);

        assertThat(query.requestedMemberId).isEqualTo(42L);
    }

    @Test
    @DisplayName("전화번호만 보내면 부서·직급은 그대로다")
    void partialUpdateOnlyTouchesSentFields() {
        FakeQuery query = new FakeQuery(profile());
        FakeCommand command = new FakeCommand();

        service(query, command, new FakeTeam(), new FakePosition(), new FakeMemberCommand(), new FakeRefreshTokenStore())
                .update(new UpdateMyProfileCommand(MEMBER_ID, COMPANY_ID, null, null, "010-9999-0000"));

        assertThat(command.teamId).isNull();
        assertThat(command.positionId).isNull();
        assertThat(command.phone).isEqualTo("010-9999-0000");
    }

    @Test
    @DisplayName("다른 회사 소속 팀으로 바꾸려 하면 거절한다")
    void rejectsTeamFromAnotherCompany() {
        FakeQuery query = new FakeQuery(profile());
        FakeTeam team = new FakeTeam();

        assertThatThrownBy(() -> service(query, new FakeCommand(), team, new FakePosition(),
                new FakeMemberCommand(), new FakeRefreshTokenStore())
                .update(new UpdateMyProfileCommand(MEMBER_ID, COMPANY_ID, 999L, null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("다른 회사 소속 직급으로 바꾸려 하면 거절한다")
    void rejectsPositionFromAnotherCompany() {
        FakeQuery query = new FakeQuery(profile());

        assertThatThrownBy(() -> service(query, new FakeCommand(), new FakeTeam(), new FakePosition(),
                new FakeMemberCommand(), new FakeRefreshTokenStore())
                .update(new UpdateMyProfileCommand(MEMBER_ID, COMPANY_ID, null, 999L, null)))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getErrorCode())
                .isEqualTo(AuthErrorCode.POSITION_NOT_FOUND);
    }

    @Test
    @DisplayName("팀장이 실제로 다른 팀으로 옮기면 강등되고 옛 팀 리더가 비워지고 리프레시가 폐기된다")
    void movingTeamDemotesExistingLeader() {
        FakeQuery query = new FakeQuery(leaderProfile());
        FakeTeam team = new FakeTeam();
        FakeMemberCommand memberCommand = new FakeMemberCommand();
        FakeRefreshTokenStore tokenStore = new FakeRefreshTokenStore();

        service(query, new FakeCommand(), team, new FakePosition(), memberCommand, tokenStore)
                .update(new UpdateMyProfileCommand(MEMBER_ID, COMPANY_ID, 55L, null, null));

        assertThat(team.clearedLeaderTeamId).isEqualTo(2L);
        assertThat(memberCommand.demotedMemberId).isEqualTo(MEMBER_ID);
        assertThat(tokenStore.revokedMemberId).isEqualTo(MEMBER_ID);
    }

    @Test
    @DisplayName("같은 팀을 다시 보내면 강등하지 않는다 — 실제로 팀을 바꾼 게 아니다")
    void resendingSameTeamDoesNotDemote() {
        FakeQuery query = new FakeQuery(leaderProfile());
        FakeMemberCommand memberCommand = new FakeMemberCommand();

        service(query, new FakeCommand(), new FakeTeam(), new FakePosition(), memberCommand,
                new FakeRefreshTokenStore())
                .update(new UpdateMyProfileCommand(MEMBER_ID, COMPANY_ID, 2L, null, null));

        assertThat(memberCommand.demotedMemberId).isNull();
    }

    @Test
    @DisplayName("직급만 바꿔도 권한(authority)은 그대로다 — 이 경로로 권한이 오르지 않는다")
    void changingPositionNeverTouchesAuthority() {
        FakeQuery query = new FakeQuery(profile());
        FakeMemberCommand memberCommand = new FakeMemberCommand();

        service(query, new FakeCommand(), new FakeTeam(), new FakePosition(), memberCommand,
                new FakeRefreshTokenStore())
                .update(new UpdateMyProfileCommand(MEMBER_ID, COMPANY_ID, null, 4L, null));

        assertThat(memberCommand.updatedRoleAndPositionMemberId).isNull();
        assertThat(memberCommand.demotedMemberId).isNull();
    }

    private MyProfileService service(MyProfileQueryPort query) {
        return service(query, new FakeCommand(), new FakeTeam(), new FakePosition(),
                new FakeMemberCommand(), new FakeRefreshTokenStore());
    }

    private MyProfileService service(MyProfileQueryPort query, MyProfileCommandPort command, TeamRepository team,
                                      PositionRepository position, MemberDirectoryCommandPort memberCommand,
                                      RefreshTokenStore tokenStore) {
        return new MyProfileService(query, command, team, position, memberCommand, tokenStore);
    }

    private static MyProfile profile() {
        return new MyProfile(
                MEMBER_ID, COMPANY_ID, "(주)테크스타트", "8AS2-G8T1",
                "이하윤", "hayun@zgroup.co.kr", "010-1000-0003",
                2L, "개발팀", "프론트엔드", 4L, "선임",
                Authority.MEMBER, false, true,
                MemberStatus.ACTIVE, LocalDate.of(2022, 5, 10), Plan.FREE, true);
    }

    private static MyProfile leaderProfile() {
        return new MyProfile(
                MEMBER_ID, COMPANY_ID, "(주)테크스타트", "8AS2-G8T1",
                "김서준", "seojun@zgroup.co.kr", "010-1000-0004",
                2L, "개발팀", "프론트엔드", 4L, "선임",
                Authority.LEADER, false, true,
                MemberStatus.ACTIVE, LocalDate.of(2021, 3, 2), Plan.FREE, true);
    }

    private static final class FakeQuery implements MyProfileQueryPort {
        private Long requestedMemberId;
        private final MyProfile profile;

        FakeQuery(MyProfile profile) {
            this.profile = profile;
        }

        @Override
        public Optional<MyProfile> findByMemberId(Long memberId) {
            this.requestedMemberId = memberId;
            return Optional.ofNullable(profile);
        }
    }

    private static final class FakeCommand implements MyProfileCommandPort {
        private Long teamId;
        private Long positionId;
        private String phone;

        @Override
        public void updateProfile(Long memberId, Long teamId, Long positionId, String phone) {
            this.teamId = teamId;
            this.positionId = positionId;
            this.phone = phone;
        }
    }

    private static final class FakeTeam implements TeamRepository {
        private Long clearedLeaderTeamId;
        private final Map<Long, Long> teamsByCompany = Map.of(2L, COMPANY_ID, 55L, COMPANY_ID);

        @Override
        public List<Team> findByCompanyId(Long companyId) {
            return List.of();
        }

        @Override
        public Optional<Team> findByIdAndCompanyId(Long id, Long companyId) {
            if (teamsByCompany.get(id) != null && teamsByCompany.get(id).equals(companyId)) {
                return Optional.of(new Team(id, companyId, "팀" + id, null));
            }
            return Optional.empty();
        }

        @Override
        public Optional<Team> findByLeaderMemberId(Long leaderMemberId) {
            return Optional.empty();
        }

        @Override
        public Team create(Long companyId, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void rename(Long id, String name) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void updateLeader(Long id, Long leaderMemberId) {
            if (leaderMemberId == null) {
                this.clearedLeaderTeamId = id;
            }
        }

        @Override
        public void delete(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByCompanyIdAndName(Long companyId, String name) {
            return false;
        }
    }

    private static final class FakePosition implements PositionRepository {
        private final Map<Long, Long> positionsByCompany = new HashMap<>(Map.of(4L, COMPANY_ID));

        @Override
        public List<Position> findByCompanyId(Long companyId) {
            return List.of();
        }

        @Override
        public Optional<Position> findByIdAndCompanyId(Long id, Long companyId) {
            if (companyId.equals(positionsByCompany.get(id))) {
                return Optional.of(new Position(id, companyId, "직급" + id, Authority.MEMBER, null));
            }
            return Optional.empty();
        }

        @Override
        public Position create(Long companyId, String name, Authority authority, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Long id, String name, Authority authority, String description) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(Long id) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean existsByCompanyIdAndName(Long companyId, String name) {
            return false;
        }
    }

    private static final class FakeMemberCommand implements MemberDirectoryCommandPort {
        private Long demotedMemberId;
        private Long updatedRoleAndPositionMemberId;

        @Override
        public void updateRoleAndPosition(Long memberId, Authority authority, Long positionId, Long roleId) {
            this.updatedRoleAndPositionMemberId = memberId;
        }

        @Override
        public void softDelete(Long memberId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void demoteToMember(Long memberId) {
            this.demotedMemberId = memberId;
        }

        @Override
        public void updateAdmin(Long memberId, boolean isAdmin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Long issue(Long companyId, Long teamId, Long positionId, Long roleId, String name,
                           String email, String passwordHash, Authority authority) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FakeRefreshTokenStore implements RefreshTokenStore {
        private Long revokedMemberId;

        @Override
        public void save(Long memberId, String jti, Duration ttl) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean exists(Long memberId, String jti) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revoke(Long memberId, String jti) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void revokeAllByMember(Long memberId) {
            this.revokedMemberId = memberId;
        }
    }
}
