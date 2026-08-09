package com.module06.backend.search.application.query;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.search.domain.model.SearchType;

public record SearchQuery(
        Long companyId,
        Long requesterMemberId,
        Long requesterTeamId,
        String requesterRole,
        boolean admin,
        String keyword,
        SearchType type,
        List<String> tags,
        LocalDate from,
        LocalDate to,
        Integer limit
) {
}
