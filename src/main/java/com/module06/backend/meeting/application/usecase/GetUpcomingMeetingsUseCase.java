package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.query.GetUpcomingMeetingsQuery;
import com.module06.backend.meeting.application.result.UpcomingMeetingListResult;

/*
 * MEET-03 Controller가 내 예정 회의 목록을 조회할 때 사용하는 인바운드 Port다.
 */
public interface GetUpcomingMeetingsUseCase {

    /* 인증 사용자가 참석자로 등록된 예정·진행 중 회의를 시간순으로 반환한다. */
    UpcomingMeetingListResult getUpcomingMeetings(GetUpcomingMeetingsQuery query);
}
