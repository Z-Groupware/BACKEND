package com.module06.backend.search.application.query;

public record RecentQueryCommand(
        Long companyId,
        Long memberId,
        String query
) {
}
