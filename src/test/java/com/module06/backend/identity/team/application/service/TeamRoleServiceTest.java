package com.module06.backend.identity.team.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.domain.model.Role;
import com.module06.backend.identity.team.application.command.CreateTeamRoleCommand;
import com.module06.backend.identity.team.application.command.RenameTeamRoleCommand;
import com.module06.backend.identity.team.application.dto.RoleNode;
import com.module06.backend.identity.team.application.service.TeamServiceTest.FakeMemberQueryPort;
import com.module06.backend.identity.team.application.service.TeamServiceTest.FakeTeamRepository;
import com.module06.backend.identity.team.domain.model.Team;

/* 부서 안 역할 CRUD(§6-10~6-12) — 부서 CRUD 와 같은 규칙(회사 스코프 · 쓰는 사람 있으면 삭제 금지)을 따른다. */
@DisplayName("부서 안 역할 CRUD")
class TeamRoleServiceTest {

    @Test
    @DisplayName("부서 아래에 역할을 만든다")
    void createsRoleUnderTeam() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");

        RoleNode node = fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "백엔드"));

        assertThat(node.name()).isEqualTo("백엔드");
        assertThat(fixture.roleRepository.findByIdAndCompanyIdAndTeamId(node.roleId(), 1L, team.id()))
                .isPresent();
    }

    @Test
    @DisplayName("앞뒤 공백은 저장 전에 정리한다")
    void stripsSurroundingWhitespace() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");

        RoleNode node = fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "  백엔드  "));

        assertThat(node.name()).isEqualTo("백엔드");
    }

    @Test
    @DisplayName("없는 부서 아래에는 만들 수 없다")
    void rejectsCreatingUnderMissingTeam() {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.service().create(new CreateTeamRoleCommand(1L, 999L, "백엔드")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 회사 부서에는 만들 수 없다")
    void rejectsCreatingUnderOtherCompanyTeam() {
        Fixture fixture = new Fixture();
        Team team = fixture.teamOf(2L, "남의회사팀");

        assertThatThrownBy(() -> fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "백엔드")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.TEAM_NOT_FOUND);
    }

    @Test
    @DisplayName("같은 부서 안 이름이 겹치면 거절한다")
    void rejectsDuplicateNameInSameTeam() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");
        fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "백엔드"));

        assertThatThrownBy(() -> fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "백엔드")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("다른 부서에 같은 이름이 있는 것은 허용한다 — 부서마다 백엔드가 있는 게 정상이다")
    void allowsSameNameInAnotherTeam() {
        Fixture fixture = new Fixture();
        Team dev = fixture.team("개발팀");
        Team platform = fixture.team("플랫폼팀");
        fixture.service().create(new CreateTeamRoleCommand(1L, dev.id(), "백엔드"));

        RoleNode node = fixture.service().create(new CreateTeamRoleCommand(1L, platform.id(), "백엔드"));

        assertThat(node.name()).isEqualTo("백엔드");
    }

    @Test
    @DisplayName("시스템 역할과 같은 이름은 만들 수 없다 — 한 select 안에 같은 이름이 두 번 뜬다")
    void rejectsSystemRoleNames() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");

        assertThatThrownBy(() -> fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "없음")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NAME_DUPLICATED);
        assertThatThrownBy(() -> fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "리더")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("이름을 바꾼다 — 구성원 행은 건드리지 않는다")
    void renamesRoleInPlace() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");
        RoleNode created = fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "백엔드"));

        RoleNode node = fixture.service()
                .rename(new RenameTeamRoleCommand(1L, team.id(), created.roleId(), "서버"));

        assertThat(node.roleId()).isEqualTo(created.roleId());
        assertThat(node.name()).isEqualTo("서버");
        assertThat(fixture.roleRepository.findByIdAndCompanyIdAndTeamId(created.roleId(), 1L, team.id()))
                .get().extracting(Role::name).isEqualTo("서버");
    }

    @Test
    @DisplayName("이름을 그대로 다시 저장해도 자기 자신과는 중복 처리하지 않는다")
    void allowsRenamingToSameName() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");
        RoleNode created = fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "백엔드"));

        RoleNode node = fixture.service()
                .rename(new RenameTeamRoleCommand(1L, team.id(), created.roleId(), "백엔드"));

        assertThat(node.name()).isEqualTo("백엔드");
    }

    @Test
    @DisplayName("바꾸려는 이름이 같은 부서의 다른 역할과 겹치면 거절한다")
    void rejectsRenamingToDuplicateName() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");
        RoleNode backend = fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "백엔드"));
        fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "프론트엔드"));

        assertThatThrownBy(() -> fixture.service()
                .rename(new RenameTeamRoleCommand(1L, team.id(), backend.roleId(), "프론트엔드")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NAME_DUPLICATED);
    }

    @Test
    @DisplayName("다른 부서의 역할 id 를 끼워 넣으면 404 로 거절한다")
    void rejectsRoleFromAnotherTeam() {
        Fixture fixture = new Fixture();
        Team dev = fixture.team("개발팀");
        Team platform = fixture.team("플랫폼팀");
        RoleNode backend = fixture.service().create(new CreateTeamRoleCommand(1L, dev.id(), "백엔드"));

        assertThatThrownBy(() -> fixture.service()
                .rename(new RenameTeamRoleCommand(1L, platform.id(), backend.roleId(), "서버")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NOT_FOUND);
    }

    @Test
    @DisplayName("남의 회사 역할 id 를 끼워 넣어도 손대지 못한다")
    void rejectsRoleFromAnotherCompany() {
        Fixture fixture = new Fixture();
        Team ours = fixture.team("개발팀");
        Team theirs = fixture.teamOf(2L, "남의회사팀");
        Long theirRoleId = fixture.roleRepository.create(2L, theirs.id(), "백엔드");

        assertThatThrownBy(() -> fixture.service().delete(1L, ours.id(), theirRoleId))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_NOT_FOUND);
        assertThat(fixture.roleRepository.all()).extracting(Role::id).contains(theirRoleId);
    }

    @Test
    @DisplayName("시스템 역할(리더 1 · 없음 2)은 수정도 삭제도 막는다")
    void rejectsEditingSystemRoles() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");
        fixture.roleRepository.seedSystemRole(1L, "리더");
        fixture.roleRepository.seedSystemRole(2L, "없음");

        assertThatThrownBy(() -> fixture.service()
                .rename(new RenameTeamRoleCommand(1L, team.id(), 1L, "팀장")))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_SYSTEM_NOT_MODIFIABLE);
        assertThatThrownBy(() -> fixture.service().delete(1L, team.id(), 2L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_SYSTEM_NOT_MODIFIABLE);
        assertThat(fixture.roleRepository.all()).extracting(Role::id).contains(1L, 2L);
    }

    @Test
    @DisplayName("쓰는 사람이 없으면 삭제된다")
    void deletesUnusedRole() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");
        RoleNode created = fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "백엔드"));

        fixture.service().delete(1L, team.id(), created.roleId());

        assertThat(fixture.roleRepository.all()).isEmpty();
    }

    @Test
    @DisplayName("그 역할인 재직자가 있으면 삭제를 거절한다 — 부서 삭제와 같은 원칙이다")
    void rejectsDeletingRoleInUse() {
        Fixture fixture = new Fixture();
        Team team = fixture.team("개발팀");
        RoleNode created = fixture.service().create(new CreateTeamRoleCommand(1L, team.id(), "백엔드"));
        fixture.memberQueryPort.assignRole(created.roleId());

        assertThatThrownBy(() -> fixture.service().delete(1L, team.id(), created.roleId()))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", AuthErrorCode.ROLE_IN_USE);
        assertThat(fixture.roleRepository.all()).hasSize(1);
    }

    /* ── 테스트 더블 묶음 — 부서 저장소·구성원 조회는 TeamServiceTest 의 것을 그대로 쓴다. ── */

    private static final class Fixture {

        private final FakeTeamRepository teamRepository = new FakeTeamRepository();
        private final FakeRoleRepository roleRepository = new FakeRoleRepository();
        private final FakeMemberQueryPort memberQueryPort = new FakeMemberQueryPort();

        Team team(String name) {
            return teamOf(1L, name);
        }

        Team teamOf(Long companyId, String name) {
            return teamRepository.create(companyId, name);
        }

        TeamRoleService service() {
            return new TeamRoleService(teamRepository, roleRepository, memberQueryPort);
        }
    }
}
