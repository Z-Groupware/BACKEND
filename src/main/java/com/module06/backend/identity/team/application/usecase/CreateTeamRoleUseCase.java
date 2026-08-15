package com.module06.backend.identity.team.application.usecase;

import com.module06.backend.identity.team.application.command.CreateTeamRoleCommand;
import com.module06.backend.identity.team.application.dto.RoleNode;

public interface CreateTeamRoleUseCase {

    RoleNode create(CreateTeamRoleCommand command);
}
