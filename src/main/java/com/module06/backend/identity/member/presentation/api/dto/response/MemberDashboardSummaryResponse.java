package com.module06.backend.identity.member.presentation.api.dto.response;

import com.module06.backend.identity.member.application.usecase.GetMemberDashboardSummaryUseCase.MemberDashboardSummary;

/**
 * 오너 대시보드 KPI 카드 중 identity(B) 소유분(전체 사원·휴직자)만 담는다. "전체 프로젝트"·
 * "마감 D-7" 은 project(C) 소유라 {@code OwnerDashboardSummaryResponse} 가 따로 낸다 —
 * FE 는 두 도메인 응답을 화면에서 합친다(이슈 #352). 필드 이름 스타일을 그쪽에 맞췄다.
 */
public record MemberDashboardSummaryResponse(long totalMemberCount, long onLeaveMemberCount) {

    public static MemberDashboardSummaryResponse from(MemberDashboardSummary summary) {
        return new MemberDashboardSummaryResponse(summary.totalMemberCount(), summary.onLeaveMemberCount());
    }
}
