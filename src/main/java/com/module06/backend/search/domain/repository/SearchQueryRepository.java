package com.module06.backend.search.domain.repository;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.search.domain.model.SearchType;

public interface SearchQueryRepository {

    long count(SearchScope scope, SearchType type, String keyword);

    List<SearchHit> search(SearchScope scope, SearchType type, String keyword, int limit);

    record SearchScope(
            Long companyId,
            Long requesterMemberId,
            Long requesterTeamId,
            String requesterRole,
            boolean admin,
            List<String> tags,
            LocalDate from,
            LocalDate to
    ) {
        public boolean companyAdmin() {
            return admin || "OWNER".equals(requesterRole) || "ADMIN".equals(requesterRole);
        }
    }

    record SearchHit(
            SearchType type,
            Long id,
            String title,
            String snippet,
            Project project,
            LocalDate date,
            String role,
            double score
    ) {
    }

    record Project(
            Long id,
            String tag,
            String name,
            String color
    ) {
    }
}
