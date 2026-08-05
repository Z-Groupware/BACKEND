package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.query.GetMeetingAttendeesQuery;
import com.module06.backend.meeting.application.result.MeetingAttendeesResult;

/*
 * RESULT-01 참석자 REST API와 회의 조회 서비스 사이의 인바운드 포트다.
 */
public interface GetMeetingAttendeesUseCase {

    /* 열람 권한을 검증하고 회의의 전체 참석자 표시 정보를 반환한다. */
    MeetingAttendeesResult getMeetingAttendees(GetMeetingAttendeesQuery query);
}
