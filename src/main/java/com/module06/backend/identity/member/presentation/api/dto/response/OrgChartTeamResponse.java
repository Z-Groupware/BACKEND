package com.module06.backend.identity.member.presentation.api.dto.response;

import java.util.List;

import com.module06.backend.identity.member.application.dto.OrgChartTeam;

public record OrgChartTeamResponse(
        Long teamId,
        String name,
        List<OrgChartSubTeamResponse> subTeams
) {

    public static OrgChartTeamResponse from(OrgChartTeam team) {
        List<OrgChartSubTeamResponse> subTeams = team.subTeams().stream()
                .map(OrgChartSubTeamResponse::from)
                .toList();
        return new OrgChartTeamResponse(team.teamId(), team.name(), subTeams);
    }
}
