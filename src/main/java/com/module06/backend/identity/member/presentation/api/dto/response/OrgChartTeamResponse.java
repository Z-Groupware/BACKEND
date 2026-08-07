package com.module06.backend.identity.member.presentation.api.dto.response;

import java.util.List;

import com.module06.backend.identity.member.application.dto.OrgChartTeam;

public record OrgChartTeamResponse(
        Long teamId,
        String name,
        List<OrgChartMemberResponse> members
) {

    public static OrgChartTeamResponse from(OrgChartTeam team) {
        List<OrgChartMemberResponse> members = team.members().stream()
                .map(OrgChartMemberResponse::from)
                .toList();
        return new OrgChartTeamResponse(team.teamId(), team.name(), members);
    }
}
