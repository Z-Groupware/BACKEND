package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.domain.model.Handover;

public interface GetHandoverUseCase {

    Handover get(Long handoverId);
}
