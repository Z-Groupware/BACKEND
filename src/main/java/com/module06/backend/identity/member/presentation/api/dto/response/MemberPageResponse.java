package com.module06.backend.identity.member.presentation.api.dto.response;

import java.util.List;

import com.module06.backend.identity.member.application.dto.MemberPage;

public record MemberPageResponse(
        long totalElements,
        int totalPages,
        boolean hasNext,
        int page,
        int size,
        List<MemberListItemResponse> content
) {

    public static MemberPageResponse from(MemberPage page) {
        List<MemberListItemResponse> content = page.content().stream()
                .map(MemberListItemResponse::from)
                .toList();
        return new MemberPageResponse(page.totalElements(), page.totalPages(), page.hasNext(),
                page.page(), page.size(), content);
    }
}
