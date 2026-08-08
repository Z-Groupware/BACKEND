package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.CancelMeetingCommand;

/* MEET-06 Controller가 시작 전 회의 취소 기능을 호출하는 인바운드 Port다. */
public interface CancelMeetingUseCase {

    /* 인증 범위 안의 회의를 취소하고 예약 슬롯을 해제한다. */
    void cancelMeeting(CancelMeetingCommand command);
}
