package com.module06.backend.identity.team.presentation.api.dto.response;

import com.module06.backend.identity.team.application.dto.TeamNode;

public record TeamNodeResponse(
        Long teamId,
        String name,
        Long leaderMemberId,
        String leaderName,
        long memberCount
) {

    public static TeamNodeResponse from(TeamNode node) {
        return new TeamNodeResponse(
                node.teamId(), node.name(), node.leaderMemberId(),
                node.leaderName(), node.memberCount());
    }
}
