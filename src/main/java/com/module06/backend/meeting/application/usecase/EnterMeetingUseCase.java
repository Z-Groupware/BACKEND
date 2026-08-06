package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.EnterMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingEntryResult;

/*
 * MEET-07 회의 입장을 프레젠테이션 계층에 제공하는 인바운드 Port다.
 */
@FunctionalInterface
public interface EnterMeetingUseCase {

    /* 예약된 참석자를 입장시키고 최초 입장이면 회의를 진행 상태로 전이한다. */
    MeetingEntryResult enterMeeting(EnterMeetingCommand command);
}
