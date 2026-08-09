package com.module06.backend.search.presentation.api.response;

import java.util.List;

import com.module06.backend.search.application.result.SearchOverviewResult;

public record SearchOverviewResponse(
        List<String> recentQueries,
        List<RecentItem> recentItems,
        List<Project> projects,
        List<Person> people
) {

    public static SearchOverviewResponse from(SearchOverviewResult result) {
        return new SearchOverviewResponse(
                result.recentQueries(),
                result.recentItems().stream().map(RecentItem::from).toList(),
                result.projects().stream().map(Project::from).toList(),
                result.people().stream().map(Person::from).toList()
        );
    }

    public record RecentItem(
            String type,
            Long id,
            String title,
            String meta
    ) {

        private static RecentItem from(SearchOverviewResult.RecentItem item) {
            return new RecentItem(
                    item.type().name(),
                    item.id(),
                    item.title(),
                    item.meta()
            );
        }
    }

    public record Project(
            Long id,
            String tag,
            String name,
            long meetingCount
    ) {

        private static Project from(SearchOverviewResult.Project project) {
            return new Project(
                    project.id(),
                    project.tag(),
                    project.name(),
                    project.meetingCount()
            );
        }
    }

    public record Person(
            Long id,
            String name,
            String role
    ) {

        private static Person from(SearchOverviewResult.Person person) {
            return new Person(
                    person.id(),
                    person.name(),
                    person.role()
            );
        }
    }
}
