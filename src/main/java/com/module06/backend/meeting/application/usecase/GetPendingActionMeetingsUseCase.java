package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.query.GetPendingActionMeetingsQuery;
import com.module06.backend.meeting.application.result.PendingActionMeetingListResult;

/*
 * MEET-10 Controller가 확정 대기 회의 목록을 조회할 때 사용하는 인바운드 Port다.
 */
public interface GetPendingActionMeetingsUseCase {

    /* 인증 사용자가 host인 종료 회의 중 분배 대기 액션이 남은 회의를 반환한다. */
    PendingActionMeetingListResult getPendingActionMeetings(GetPendingActionMeetingsQuery query);
}
