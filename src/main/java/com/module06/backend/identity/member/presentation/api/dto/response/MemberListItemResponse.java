package com.module06.backend.identity.member.presentation.api.dto.response;

import java.time.LocalDate;

import com.module06.backend.identity.member.application.dto.MemberListItem;

public record MemberListItemResponse(
        Long memberId,
        String name,
        String teamName,
        String positionName,
        String role,
        boolean isAdmin,
        String roleLabel,
        String workStatus,
        LocalDate joinedOn
) {

    public static MemberListItemResponse from(MemberListItem item) {
        return new MemberListItemResponse(
                item.memberId(), item.name(), item.teamName(), item.positionName(),
                item.role().name(), item.isAdmin(), item.roleLabel(),
                item.workStatus().name(), item.joinedOn());
    }
}
