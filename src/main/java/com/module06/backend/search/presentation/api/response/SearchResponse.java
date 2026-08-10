package com.module06.backend.search.presentation.api.response;

import java.util.List;

import com.module06.backend.search.application.result.SearchResult;

public record SearchResponse(
        String query,
        Counts counts,
        List<Item> results
) {

    public static SearchResponse from(SearchResult result) {
        return new SearchResponse(
                result.query(),
                Counts.from(result.counts()),
                result.results().stream()
                        .map(Item::from)
                        .toList()
        );
    }

    public record Counts(
            long all,
            long meeting,
            long action,
            long project,
            long person
    ) {

        private static Counts from(SearchResult.Counts counts) {
            return new Counts(
                    counts.all(),
                    counts.meeting(),
                    counts.action(),
                    counts.project(),
                    counts.person()
            );
        }
    }

    public record Item(
            String type,
            Long id,
            String title,
            String snippet,
            Project project,
            String date,
            String role,
            double score
    ) {

        private static Item from(SearchResult.Item item) {
            return new Item(
                    item.type().name(),
                    item.id(),
                    item.title(),
                    item.snippet(),
                    item.project() == null ? null : Project.from(item.project()),
                    item.date() == null ? null : item.date().toString(),
                    item.role(),
                    item.score()
            );
        }
    }

    public record Project(
            Long id,
            String tag,
            String name,
            String color
    ) {

        private static Project from(SearchResult.Project project) {
            return new Project(
                    project.id(),
                    project.tag(),
                    project.name(),
                    project.color()
            );
        }
    }
}
