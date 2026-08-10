package com.module06.backend.handover.application.usecase;

import java.util.List;

public interface GetPendingAttributionListUseCase {

    List<GetHandoverListUseCase.HandoverSummary> listPendingAttribution(Long companyId);
}
