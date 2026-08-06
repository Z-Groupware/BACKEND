package com.module06.backend.identity.team.application.command;

public record CreateTeamCommand(Long companyId, String name, Long parentTeamId) {
}
