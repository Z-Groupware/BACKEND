package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.query.GetMeetingDetailQuery;
import com.module06.backend.meeting.application.result.MeetingDetailResult;

/*
 * MEET-04 회의 상세 조회를 프레젠테이션 계층에 공개하는 인바운드 Port다.
 */
public interface GetMeetingDetailUseCase {

    /* 인증 사용자의 열람 범위에서 회의 메타와 참석자 표시 정보를 조회한다. */
    MeetingDetailResult getMeetingDetail(GetMeetingDetailQuery query);
}
