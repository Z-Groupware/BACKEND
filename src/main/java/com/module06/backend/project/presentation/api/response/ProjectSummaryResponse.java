package com.module06.backend.project.presentation.api.response;

import java.time.LocalDate;

import com.module06.backend.project.application.usecase.GetProjectListUseCase.ProjectListItem;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;

/* comment.
    프로젝트 목록 행 응답 DTO. actionCount·completedActionCount·progressPct는 action BC가
    갖춰진 2026-08-09부로 실집계로 채운다. meetingCount는 여전히 0 고정 — meeting(D) 도메인
    Port가 아직 없다(TBD, 모성진에게 요청함).
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

    // 생성·수정 직후 응답용 — 방금 만든/고친 프로젝트 하나만 돌려주므로 액션 집계가 필요 없다.
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

    public static ProjectSummaryResponse from(ProjectListItem item) {
        Project project = item.project();
        double progressPct = item.actionCount() == 0
                ? 0.0
                : (double) item.completedActionCount() / item.actionCount() * 100;

        return new ProjectSummaryResponse(
                project.getId(),
                project.getTag(),
                project.getColor(),
                project.getName(),
                project.getStatus(),
                project.getDueDate(),
                project.getTeamIds().size(),
                item.actionCount(),
                item.completedActionCount(),
                0,
                progressPct
        );
    }
}
