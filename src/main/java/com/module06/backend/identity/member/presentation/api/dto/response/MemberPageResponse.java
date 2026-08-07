package com.module06.backend.identity.member.presentation.api.dto.response;

import java.util.List;

import com.module06.backend.identity.member.application.dto.MemberPage;

public record MemberPageResponse(
        long totalCount,
        int page,
        int size,
        List<MemberListItemResponse> content
) {

    public static MemberPageResponse from(MemberPage page) {
        List<MemberListItemResponse> content = page.content().stream()
                .map(MemberListItemResponse::from)
                .toList();
        return new MemberPageResponse(page.totalCount(), page.page(), page.size(), content);
    }
}
