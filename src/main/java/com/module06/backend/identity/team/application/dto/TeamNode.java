package com.module06.backend.identity.team.application.dto;

import java.util.List;

public record TeamNode(
        Long teamId,
        String name,
        Long parentTeamId,
        Long leaderMemberId,
        String leaderName,
        long memberCount,
        List<TeamNode> children
) {
}
