package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.application.command.ReassignItemCommand;
import com.module06.backend.handover.domain.model.Handover;

public interface ReassignHandoverItemUseCase {

    Handover reassignItem(ReassignItemCommand command);
}
