package com.module06.backend.identity.member.presentation.api.dto.response;

import java.util.List;

import com.module06.backend.identity.member.application.dto.OrgChartSubTeam;

public record OrgChartSubTeamResponse(
        String roleLabel,
        List<OrgChartMemberResponse> members
) {

    public static OrgChartSubTeamResponse from(OrgChartSubTeam subTeam) {
        List<OrgChartMemberResponse> members = subTeam.members().stream()
                .map(OrgChartMemberResponse::from)
                .toList();
        return new OrgChartSubTeamResponse(subTeam.roleLabel(), members);
    }
}
