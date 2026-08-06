package com.module06.backend.identity.position.application.command;

import com.module06.backend.identity.member.domain.model.Authority;

public record CreatePositionCommand(Long companyId, String name, Authority authority, String description) {
}
