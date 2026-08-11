package com.module06.backend.project.presentation.api.response;

import com.module06.backend.project.application.usecase.GetOwnerDashboardSummaryUseCase.OwnerDashboardSummary;

/* comment.
    오너 대시보드 KPI 카드 중 project(C) 소유분(전체 프로젝트·마감 D-7)만 담는다. "전체 사원"·
    "휴직자"는 identity/leave(B) 소유라 이 응답에 없다 — FE는 두 도메인 응답을 화면에서 합친다
    (이슈 #352).
*/
public record OwnerDashboardSummaryResponse(long totalProjectCount, long dueSoonProjectCount) {

    public static OwnerDashboardSummaryResponse from(OwnerDashboardSummary summary) {
        return new OwnerDashboardSummaryResponse(summary.totalProjectCount(), summary.dueSoonProjectCount());
    }
}
