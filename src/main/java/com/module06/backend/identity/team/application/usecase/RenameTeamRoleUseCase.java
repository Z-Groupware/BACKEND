package com.module06.backend.identity.team.application.usecase;

import com.module06.backend.identity.team.application.command.RenameTeamRoleCommand;
import com.module06.backend.identity.team.application.dto.RoleNode;

public interface RenameTeamRoleUseCase {

    RoleNode rename(RenameTeamRoleCommand command);
}
