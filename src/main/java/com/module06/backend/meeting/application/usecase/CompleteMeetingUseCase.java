package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.CompleteMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingCompletionResult;

/* MEET-08 프레젠테이션 계층이 호출하는 회의 종료 인바운드 Port다. */
public interface CompleteMeetingUseCase {

    /* 회의와 캡처 세션을 종료하고 비동기 분석 요청 이벤트를 발행한다. */
    MeetingCompletionResult completeMeeting(CompleteMeetingCommand command);
}
