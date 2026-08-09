package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.application.command.CreateHandoverCommand;
import com.module06.backend.handover.domain.model.Handover;

public interface CreateHandoverUseCase {

    Handover create(CreateHandoverCommand command);
}
