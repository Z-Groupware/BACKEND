package com.module06.backend.identity.member.presentation.api.dto.response;

import com.module06.backend.identity.member.application.dto.OrgChartMember;

public record OrgChartMemberResponse(
        Long memberId,
        String name,
        String positionName,
        String role
) {

    public static OrgChartMemberResponse from(OrgChartMember member) {
        return new OrgChartMemberResponse(member.memberId(), member.name(), member.positionName(),
                member.role().name());
    }
}
