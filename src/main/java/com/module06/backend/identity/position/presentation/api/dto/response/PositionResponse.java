package com.module06.backend.identity.position.presentation.api.dto.response;

import com.module06.backend.identity.position.application.dto.PositionSummary;

public record PositionResponse(
        Long jobPositionId,
        String name,
        String authority,
        String description,
        long memberCount
) {

    public static PositionResponse from(PositionSummary summary) {
        return new PositionResponse(
                summary.id(), summary.name(), summary.authority().name(), summary.description(),
                summary.memberCount());
    }
}
