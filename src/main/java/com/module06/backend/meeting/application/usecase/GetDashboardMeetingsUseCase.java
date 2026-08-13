package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.query.GetDashboardMeetingsQuery;
import com.module06.backend.meeting.application.result.DashboardMeetingListResult;

/*
 * MEET-17 Controller가 대시보드 최근 회의 카드를 조회할 때 사용하는 인바운드 Port다.
 */
@FunctionalInterface
public interface GetDashboardMeetingsUseCase {

    /* 스코프·요청자 조건으로 대시보드에 노출할 최근 회의를 반환한다. */
    DashboardMeetingListResult getDashboardMeetings(GetDashboardMeetingsQuery query);
}
