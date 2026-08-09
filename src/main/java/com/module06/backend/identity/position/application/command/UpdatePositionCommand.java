package com.module06.backend.identity.position.application.command;

import com.module06.backend.identity.member.domain.model.Authority;

public record UpdatePositionCommand(
        Long companyId,
        Long positionId,
        String name,
        Authority authority,
        String description
) {
}
