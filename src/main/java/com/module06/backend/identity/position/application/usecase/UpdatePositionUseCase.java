package com.module06.backend.identity.position.application.usecase;

import com.module06.backend.identity.position.application.command.UpdatePositionCommand;
import com.module06.backend.identity.position.application.dto.PositionSummary;

public interface UpdatePositionUseCase {
    PositionSummary update(UpdatePositionCommand command);
}
