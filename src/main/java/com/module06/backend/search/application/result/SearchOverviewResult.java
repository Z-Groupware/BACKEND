package com.module06.backend.search.application.result;

import java.util.List;

import com.module06.backend.search.domain.model.SearchType;

public record SearchOverviewResult(
        List<String> recentQueries,
        List<RecentItem> recentItems,
        List<Project> projects,
        List<Person> people
) {

    public record RecentItem(
            SearchType type,
            Long id,
            String title,
            String meta
    ) {
    }

    public record Project(
            Long id,
            String tag,
            String name,
            long meetingCount
    ) {
    }

    public record Person(
            Long id,
            String name,
            String role
    ) {
    }
}
