package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.ReplaceMeetingAttendeesCommand;
import com.module06.backend.meeting.application.result.MeetingAttendeeUpdateResult;

/*
 * MEET-09 Controller가 회의 참석자 명단 전체를 교체할 때 사용하는 인바운드 Port다.
 */
public interface ReplaceMeetingAttendeesUseCase {

    /* 권한과 회의 상태 및 구성원 유효성을 검증한 뒤 참석자 명단을 원자적으로 교체한다. */
    MeetingAttendeeUpdateResult replaceMeetingAttendees(ReplaceMeetingAttendeesCommand command);
}
