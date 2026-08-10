package com.module06.backend.project.presentation.api.response;

import java.time.LocalDate;
import java.util.List;

import com.module06.backend.project.application.usecase.GetProjectListUseCase.ProjectListItem;
import com.module06.backend.project.domain.model.Project;
import com.module06.backend.project.domain.model.ProjectStatus;

/* comment.
    프로젝트 목록 행 응답 DTO. actionCount·completedActionCount·progressPct·meetingCount 전부
    2026-08-09부로 실집계 — meeting(D)이 countMeetingsByProjectIds 배치 계약을 제공하면서
    meetingCount도 채워진다(취소 회의 제외, MeetingQueryPortDelegatingAdapter 경유).
    teamNames는 부서 칩 표시용 — team(B) 참조 테이블 배치 조회로 채운다(2026-08-09, 윤종호 확인).
*/
public record ProjectSummaryResponse(
        Long id,
        String tag,
        String color,
        String name,
        ProjectStatus status,
        LocalDate startDate,
        LocalDate dueDate,
        int teamCount,
        int actionCount,
        int completedActionCount,
        int meetingCount,
        double progressPct,
        List<String> teamNames
) {

    // 생성·수정 직후 응답용 — 방금 만든/고친 프로젝트 하나만 돌려주므로 액션 집계·부서 이름 조회가 필요 없다.
    public static ProjectSummaryResponse from(Project project) {
        return new ProjectSummaryResponse(
                project.getId(),
                project.getTag(),
                project.getColor(),
                project.getName(),
                project.getStatus(),
                project.getStartDate(),
                project.getDueDate(),
                project.getTeamIds().size(),
                0,
                0,
                0,
                0.0,
                List.of()
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
                project.getStartDate(),
                project.getDueDate(),
                project.getTeamIds().size(),
                item.actionCount(),
                item.completedActionCount(),
                item.meetingCount(),
                progressPct,
                item.teamNames()
        );
    }
}
