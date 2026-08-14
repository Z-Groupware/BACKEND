package com.module06.backend.identity.team.infrastructure.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import com.module06.backend.identity.team.application.port.out.TeamRoleQueryPort;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TeamRoleQueryAdapter implements TeamRoleQueryPort {

    /**
     * 시스템 역할 "리더"(V2.3.9)는 목록에서 뺀다. 팀장인 사람을 표시하는 값이지 사용자가 고르는
     * 값이 아니다 — select 에 올리면 인가 축인 {@code Authority.LEADER} 와 헷갈리고, 역할을
     * "리더"로 골라도 팀장이 되지는 않는다. 같은 시드의 "없음"은 반대로 그대로 남긴다(역할
     * 미부여를 뜻하는 실제 행이라 선택지가 되어야 한다).
     */
    private static final long ROLE_LEADER_ID = 1L;

    private final SpringDataTeamRoleRefRepository repository;

    @Override
    public List<RoleSummary> findAssignableByCompany(Long companyId) {
        return repository.findByCompanyIdOrCompanyIdIsNull(companyId).stream()
                .filter(role -> role.getId() != ROLE_LEADER_ID)
                .map(role -> new RoleSummary(role.getId(), role.getTeamId(), role.getName()))
                .toList();
    }
}
