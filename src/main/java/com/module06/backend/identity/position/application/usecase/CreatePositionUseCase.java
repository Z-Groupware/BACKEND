package com.module06.backend.identity.position.application.usecase;

import com.module06.backend.identity.position.application.command.CreatePositionCommand;
import com.module06.backend.identity.position.application.dto.PositionSummary;

public interface CreatePositionUseCase {
    PositionSummary create(CreatePositionCommand command);
}
