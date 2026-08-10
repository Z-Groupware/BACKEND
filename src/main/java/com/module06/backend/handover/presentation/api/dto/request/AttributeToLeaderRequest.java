package com.module06.backend.handover.presentation.api.dto.request;

import com.module06.backend.handover.application.command.AttributeHandoverToLeaderCommand;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;

public record AttributeToLeaderRequest(
        @NotNull Long newLeaderId
) {

    public AttributeHandoverToLeaderCommand toCommand(Long handoverId, Long requesterCompanyId,
                                                      LocalDateTime attributedAt) {
        return new AttributeHandoverToLeaderCommand(handoverId, newLeaderId, requesterCompanyId, attributedAt);
    }
}
