package com.module06.backend.search.application.result;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.search.domain.model.SearchType;

public record SearchResult(
        String query,
        Counts counts,
        List<Item> results
) {

    public record Counts(
            long all,
            long meeting,
            long action,
            long project,
            long person
    ) {
    }

    public record Item(
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

    public record Project(
            Long id,
            String tag,
            String name,
            String color
    ) {
    }
}
