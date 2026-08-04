package com.module06.backend.handover.application.command;

import java.util.Objects;

public record FinalizeHandoverInsightsCommand(
    Long handoverId,
    Long departureMemberId
) {

    public FinalizeHandoverInsightsCommand {
        Objects.requireNonNull(handoverId, "handoverId must not be null");
        Objects.requireNonNull(departureMemberId, "departureMemberId must not be null");
    }
}
