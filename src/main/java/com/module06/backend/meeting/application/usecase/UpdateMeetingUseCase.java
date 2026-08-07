package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.UpdateMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingUpdateResult;

/*
 * MEET-05 Controller가 회의 정보 수정 기능을 호출하는 인바운드 Port다.
 */
public interface UpdateMeetingUseCase {

    /* 인증 범위와 PATCH 필드로 예약 회의를 수정하고 최종 표시 정보를 반환한다. */
    MeetingUpdateResult updateMeeting(UpdateMeetingCommand command);
}
