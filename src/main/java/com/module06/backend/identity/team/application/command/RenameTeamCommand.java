package com.module06.backend.identity.team.application.command;

public record RenameTeamCommand(Long companyId, Long teamId, String name) {
}
