package com.module06.backend.identity.position.application.usecase;

import java.util.List;

import com.module06.backend.identity.position.application.dto.PositionSummary;

public interface GetPositionsUseCase {

    List<PositionSummary> getPositions(Long companyId);
}
