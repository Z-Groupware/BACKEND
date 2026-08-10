package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.application.command.AttributeHandoverToLeaderCommand;
import com.module06.backend.handover.domain.model.Handover;

public interface AttributeHandoverToLeaderUseCase {

    Handover attributeToNewLeader(AttributeHandoverToLeaderCommand command);
}
