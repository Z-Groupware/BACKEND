package com.module06.backend.handover.application.command;

import java.time.LocalDateTime;

public record AttributeHandoverToLeaderCommand(
        Long handoverId,
        Long ownerId,
        Long newLeaderId,
        LocalDateTime attributedAt
) {
}
