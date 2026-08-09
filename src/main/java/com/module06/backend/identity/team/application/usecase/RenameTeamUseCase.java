package com.module06.backend.identity.team.application.usecase;

import com.module06.backend.identity.team.application.command.RenameTeamCommand;
import com.module06.backend.identity.team.application.dto.TeamNode;

public interface RenameTeamUseCase {
    TeamNode rename(RenameTeamCommand command);
}
