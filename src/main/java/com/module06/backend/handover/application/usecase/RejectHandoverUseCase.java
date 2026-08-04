package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.application.command.RejectHandoverCommand;
import com.module06.backend.handover.domain.model.Handover;

public interface RejectHandoverUseCase {

    Handover reject(RejectHandoverCommand command);
}
