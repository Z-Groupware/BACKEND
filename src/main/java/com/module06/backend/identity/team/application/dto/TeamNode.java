package com.module06.backend.identity.team.application.dto;

public record TeamNode(
        Long teamId,
        String name,
        Long leaderMemberId,
        String leaderName,
        long memberCount
) {
}
