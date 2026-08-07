package com.module06.backend.identity.member.application.dto;

import java.util.List;

public record MemberPage(
        long totalCount,
        int page,
        int size,
        List<MemberListItem> content
) {
}
