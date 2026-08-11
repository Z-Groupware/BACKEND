package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.query.GetStalledSummaryMeetingsQuery;
import com.module06.backend.meeting.application.result.StalledSummaryMeetingListResult;

/* MEET-15 요약 중단·실패 회의 목록 조회를 프레젠테이션 계층에 공개하는 인바운드 Port다. */
public interface GetStalledSummaryMeetingsUseCase {

    /* 로그인 사용자가 개설한 종료 회의 중 요약에 문제가 생긴 회의를 조회한다. */
    StalledSummaryMeetingListResult getStalledSummaryMeetings(GetStalledSummaryMeetingsQuery query);
}
