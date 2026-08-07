package com.module06.backend.identity.member.presentation.api.dto.response;

import com.module06.backend.identity.member.application.dto.IssuedMember;

public record IssuedMemberResponse(
        Long memberId,
        String name,
        String email,
        String teamName,
        String positionName,
        String role,
        boolean isAdmin,
        String workStatus
) {

    public static IssuedMemberResponse from(IssuedMember member) {
        return new IssuedMemberResponse(member.memberId(), member.name(), member.email(), member.teamName(),
                member.positionName(), member.role().name(), member.isAdmin(), member.workStatus().name());
    }
}
