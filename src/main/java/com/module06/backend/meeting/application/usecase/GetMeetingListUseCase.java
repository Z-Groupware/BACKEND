package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.query.GetMeetingListQuery;
import com.module06.backend.meeting.application.result.MeetingListResult;

/*
 * MEET-02 회의 목록 필터 조회를 프레젠테이션 계층에 공개하는 인바운드 Port다.
 */
@FunctionalInterface
public interface GetMeetingListUseCase {

    /* 인증 사용자에게 허용된 회의를 필터·페이징해 반환한다. */
    MeetingListResult getMeetings(GetMeetingListQuery query);
}
