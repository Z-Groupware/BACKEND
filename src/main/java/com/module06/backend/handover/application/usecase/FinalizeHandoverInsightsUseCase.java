package com.module06.backend.handover.application.usecase;

import com.module06.backend.handover.application.command.FinalizeHandoverInsightsCommand;

public interface FinalizeHandoverInsightsUseCase {

    void finalizeInsights(FinalizeHandoverInsightsCommand command);
}
