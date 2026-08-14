package com.module06.backend.identity.team.application.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.identity.auth.domain.exception.AuthErrorCode;
import com.module06.backend.identity.member.domain.model.Role;
import com.module06.backend.identity.member.domain.repository.RoleRepository;
import com.module06.backend.identity.team.application.command.CreateTeamRoleCommand;
import com.module06.backend.identity.team.application.command.RenameTeamRoleCommand;
import com.module06.backend.identity.team.application.dto.RoleNode;
import com.module06.backend.identity.team.application.port.out.TeamMemberQueryPort;
import com.module06.backend.identity.team.application.usecase.CreateTeamRoleUseCase;
import com.module06.backend.identity.team.application.usecase.DeleteTeamRoleUseCase;
import com.module06.backend.identity.team.application.usecase.RenameTeamRoleUseCase;
import com.module06.backend.identity.team.domain.repository.TeamRepository;

import lombok.RequiredArgsConstructor;

/**
 * 부서 안 "역할" CRUD(§6-10~6-12). 기업 설정 화면의 부서 체계 편집 탭이 쓴다.
 *
 * <p>온보딩 커밋(§4-1)이 한 번 만든 뒤로는 바꿀 수 없던 구성을 여기서 계속 편집한다. 조회는
 * 여기 없다 — 부서 목록({@code GET /api/teams})이 부서마다 그 부서의 역할을 이미 함께 싣는다
 * ({@code TeamRoleQueryPort} 참조). 만들고 바꾼 결과도 그 응답과 같은 모양({@link RoleNode})으로
 * 돌려주므로, 화면이 목록을 다시 받지 않고 그대로 끼워 넣을 수 있다.
 *
 * <p><b>시스템 역할</b>(id 1 리더 · 2 없음, V2.3.9)은 이 API 의 대상이 아니다. 그 두 행은
 * {@code company_id}·{@code team_id} 가 NULL 이라 회사·부서 스코프 조회에 애초에 잡히지 않지만,
 * 그대로 두면 404("없는 역할")로 답하게 된다 — 실제로는 존재하되 건드릴 수 없는 값이므로
 * id 를 먼저 보고 403 으로 구분한다.
 *
 * <p><b>역할을 다른 부서로 옮기는 기능은 없다.</b> 옮기면 그 역할을 달고 있던 사람들이 자기
 * 부서에 없는 역할을 단 채로 남는다 — 화면에도 그런 조작이 없다.
 */
@Service
@RequiredArgsConstructor
public class TeamRoleService implements CreateTeamRoleUseCase, RenameTeamRoleUseCase, DeleteTeamRoleUseCase {

    private final TeamRepository teamRepository;
    private final RoleRepository roleRepository;
    private final TeamMemberQueryPort memberQueryPort;

    @Override
    @Transactional
    public RoleNode create(CreateTeamRoleCommand command) {
        assertTeamExists(command.companyId(), command.teamId());

        String name = normalize(command.name());
        assertNotSystemRoleName(name);
        if (roleRepository.existsByTeamIdAndName(command.teamId(), name)) {
            throw new BusinessException(AuthErrorCode.ROLE_NAME_DUPLICATED);
        }

        Long roleId = roleRepository.create(command.companyId(), command.teamId(), name);
        return new RoleNode(roleId, name);
    }

    /**
     * 이름만 바꾼다. 이미 배정된 사람들에게는 자동으로 반영된다 — {@code member.role_id} 가 이
     * 행을 가리키는 참조라 조회하는 쪽이 바뀐 이름을 그대로 읽는다(구성원 행은 건드리지 않는다).
     */
    @Override
    @Transactional
    public RoleNode rename(RenameTeamRoleCommand command) {
        Role role = findEditableRole(command.companyId(), command.teamId(), command.roleId());

        String name = normalize(command.name());
        assertNotSystemRoleName(name);
        boolean renamingToOtherName = !name.equals(role.name());
        if (renamingToOtherName && roleRepository.existsByTeamIdAndName(command.teamId(), name)) {
            throw new BusinessException(AuthErrorCode.ROLE_NAME_DUPLICATED);
        }

        roleRepository.rename(role.id(), name);
        return new RoleNode(role.id(), name);
    }

    /**
     * 부서 삭제(§6-4)와 같은 원칙 — 그 역할인 재직자가 있으면 막는다. 배정을 자동으로 "없음"으로
     * 내리면 그 사람이 뭘 하던 사람인지 회사 안에서 조용히 지워진다.
     *
     * <p>퇴사자는 세지 않는다. 퇴사자가 든 역할까지 세면 화면에 보이지도 않는 사람 때문에 역할을
     * 영원히 못 지우게 된다. 남는 참조는 끊기지만 읽는 쪽이 없다 — 역할 이름을 읽는 경로
     * (구성원 목록·상세 §7-1/§7-3, 내 프로필)는 모두 재직자만 조회한다.
     */
    @Override
    @Transactional
    public void delete(Long companyId, Long teamId, Long roleId) {
        Role role = findEditableRole(companyId, teamId, roleId);

        if (memberQueryPort.hasActiveMembersWithRole(role.id())) {
            throw new BusinessException(AuthErrorCode.ROLE_IN_USE);
        }
        roleRepository.delete(role.id());
    }

    private void assertTeamExists(Long companyId, Long teamId) {
        if (teamRepository.findByIdAndCompanyId(teamId, companyId).isEmpty()) {
            throw new BusinessException(AuthErrorCode.TEAM_NOT_FOUND);
        }
    }

    private Role findEditableRole(Long companyId, Long teamId, Long roleId) {
        if (Role.isSystemRole(roleId)) {
            throw new BusinessException(AuthErrorCode.ROLE_SYSTEM_NOT_MODIFIABLE);
        }
        assertTeamExists(companyId, teamId);
        return roleRepository.findByIdAndCompanyIdAndTeamId(roleId, companyId, teamId)
                .orElseThrow(() -> new BusinessException(AuthErrorCode.ROLE_NOT_FOUND));
    }

    /**
     * 시드 역할과 같은 이름은 쓰지 못하게 한다.
     *
     * <p>"없음"은 부서마다의 역할 목록에 항상 함께 실린다(TeamService#rolesOf) — 같은 이름이 한
     * select 안에 두 번 뜨면 사용자가 무엇을 고른 건지 알 수 없다. "리더"는 목록에서 빠져 있지만
     * (TeamRoleQueryAdapter) 인가 축인 {@code Authority.LEADER} 와 헷갈리는 이름이라 같은 이유로
     * 막는다 — 역할을 "리더"로 골라도 팀장이 되지는 않는다.
     */
    private void assertNotSystemRoleName(String name) {
        if ("없음".equals(name) || "리더".equals(name)) {
            throw new BusinessException(AuthErrorCode.ROLE_NAME_DUPLICATED);
        }
    }

    /** 앞뒤 공백만 정리한다 — 이름 중복 비교(콜레이션이 끝 공백을 무시한다)와 저장 값을 맞춘다. */
    private String normalize(String name) {
        return name.strip();
    }
}
