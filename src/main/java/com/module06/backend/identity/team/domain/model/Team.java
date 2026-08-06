package com.module06.backend.identity.team.domain.model;

public record Team(Long id, Long companyId, String name, Long parentTeamId, Long leaderMemberId) {
}
