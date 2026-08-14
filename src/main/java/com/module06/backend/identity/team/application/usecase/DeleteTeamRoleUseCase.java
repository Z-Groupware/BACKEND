package com.module06.backend.identity.team.application.usecase;

public interface DeleteTeamRoleUseCase {

    void delete(Long companyId, Long teamId, Long roleId);
}
