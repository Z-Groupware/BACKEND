package com.module06.backend.action.presentation.api.response;

import com.module06.backend.action.application.usecase.GetTeamDashboardSummaryUseCase.TeamDashboardSummary;

/* comment.
    팀 대시보드 KPI 카드 4종 응답. 이슈 #352 — 전부 action(C) 소유 데이터.
*/
public record TeamDashboardSummaryResponse(
        long teamActionCount, long teamMemberActionCount, long myActionCount, long completedActionCount) {

    public static TeamDashboardSummaryResponse from(TeamDashboardSummary summary) {
        return new TeamDashboardSummaryResponse(
                summary.teamActionCount(), summary.teamMemberActionCount(),
                summary.myActionCount(), summary.completedActionCount());
    }
}
