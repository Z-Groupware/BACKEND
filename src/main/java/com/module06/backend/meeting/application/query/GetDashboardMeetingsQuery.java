package com.module06.backend.meeting.application.query;

import com.module06.backend.meeting.domain.model.DashboardMeetingScope;

/*
 * MEET-17 대시보드 최근 회의 조회에 필요한 인증·요청 값을 묶은 Query다.
 *
 * scope는 역할만으로 범위를 추론하지 않고 요청자가 명시하며,
 * companyId·requesterMemberId·requesterTeamId·requesterRole은 Access Token principal에서만 받는다.
 */
public record GetDashboardMeetingsQuery(
        Long companyId,
        Long requesterMemberId,
        Long requesterTeamId,
        String requesterRole,
        DashboardMeetingScope scope,
        Integer limit
) {
}
