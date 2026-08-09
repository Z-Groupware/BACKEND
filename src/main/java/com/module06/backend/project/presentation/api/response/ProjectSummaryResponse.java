package com.module06.backend.project.presentation.api.response;

import java.time.LocalDate;

import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;

/* comment.
    프로젝트 목록 행 응답 DTO. actionCount·completedActionCount·meetingCount·progressPct는
    action 도메인·meeting Port 미비로 현재 0 고정(TBD, 2026-08-05).
*/
public record ProjectSummaryResponse(
        Long id,
        String tag,
        String color,
        String name,
        ProjectStatus status,
        LocalDate dueDate,
        int teamCount,
        int actionCount,
        int completedActionCount,
        int meetingCount,
        double progressPct
) {

    public static ProjectSummaryResponse from(Project project) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getTag(),
                project.getColor(),
                project.getName(),
                project.getStatus(),
                project.getDueDate(),
                project.getTeamIds().size(),
                0,
                0,
                0,
                0.0
        );
    }
}
