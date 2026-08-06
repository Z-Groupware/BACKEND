package com.module06.backend.identity.position.application.dto;

import com.module06.backend.identity.member.domain.model.Authority;

public record PositionSummary(
        Long id,
        String name,
        Authority authority,
        String description,
        long memberCount
) {
}
