package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.domain.model.Handover;

import java.time.LocalDateTime;

public interface CompleteHandoverUseCase {

    Handover complete(Long handoverId, Long leaderId, LocalDateTime approvedAt);
}
