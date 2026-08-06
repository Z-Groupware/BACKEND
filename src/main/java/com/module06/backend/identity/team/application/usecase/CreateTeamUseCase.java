package com.module06.backend.identity.team.application.usecase;

import com.module06.backend.identity.team.application.command.CreateTeamCommand;
import com.module06.backend.identity.team.application.dto.TeamNode;

public interface CreateTeamUseCase {
    TeamNode create(CreateTeamCommand command);
}
