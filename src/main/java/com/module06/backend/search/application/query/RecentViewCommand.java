package com.module06.backend.search.application.query;

import com.module06.backend.search.domain.model.SearchType;

public record RecentViewCommand(
        Long companyId,
        Long memberId,
        SearchType type,
        Long id
) {
}
