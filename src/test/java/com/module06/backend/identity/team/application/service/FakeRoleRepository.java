package com.module06.backend.identity.team.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.module06.backend.identity.member.domain.model.Role;
import com.module06.backend.identity.member.domain.repository.RoleRepository;

/**
 * 역할 저장소 테스트 더블.
 *
 * <p>회사·부서 스코프 조회가 시스템 역할(company_id·team_id 가 NULL)을 걸러내는 실제 동작을
 * 그대로 흉내 낸다 — {@code seedSystemRole} 로 시드 행을 넣어도 편집 대상으로는 잡히지 않아야 한다.
 */
final class FakeRoleRepository implements RoleRepository {

    private final List<Role> roles = new ArrayList<>();
    private long nextId = 100L;

    /** 시스템 역할(리더·없음, V2.3.9) — company_id·team_id 가 NULL 인 전 회사 공용 행. */
    void seedSystemRole(Long id, String name) {
        roles.add(new Role(id, null, null, name));
    }

    List<Role> all() {
        return List.copyOf(roles);
    }

    @Override
    public Long create(Long companyId, Long teamId, String name) {
        Role role = new Role(nextId++, companyId, teamId, name);
        roles.add(role);
        return role.id();
    }

    @Override
    public Optional<Role> findByIdAndCompanyIdAndTeamId(Long roleId, Long companyId, Long teamId) {
        return roles.stream()
                .filter(r -> r.id().equals(roleId) && companyId.equals(r.companyId()) && teamId.equals(r.teamId()))
                .findFirst();
    }

    @Override
    public boolean existsByTeamIdAndName(Long teamId, String name) {
        return roles.stream().anyMatch(r -> teamId.equals(r.teamId()) && r.name().equals(name));
    }

    @Override
    public void rename(Long roleId, String name) {
        roles.replaceAll(r -> r.id().equals(roleId)
                ? new Role(r.id(), r.companyId(), r.teamId(), name)
                : r);
    }

    @Override
    public void delete(Long roleId) {
        roles.removeIf(r -> r.id().equals(roleId));
    }
}
