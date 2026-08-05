package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.CreateMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingCreationResult;

/*
 * MEET-01 회의 개설 API와 애플리케이션 서비스 사이의 인바운드 포트다.
 */
public interface CreateMeetingUseCase {

    /* 검증된 요청 조건으로 회의를 예약하고 응답용 결과를 반환한다. */
    MeetingCreationResult createMeeting(CreateMeetingCommand command);
}
