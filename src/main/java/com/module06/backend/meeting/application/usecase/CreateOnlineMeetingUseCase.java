package com.module06.backend.meeting.application.usecase;

import com.module06.backend.meeting.application.command.CreateOnlineMeetingCommand;
import com.module06.backend.meeting.application.result.OnlineMeetingCreationResult;

/*
 * MEET-18 비대면 회의 개설 API와 애플리케이션 계층 사이의 인바운드 포트다.
 */
public interface CreateOnlineMeetingUseCase {

    /* 회의실과 예약 시간 없이 검증된 요청으로 비대면 회의를 개설하고 응답용 결과를 반환한다. */
    OnlineMeetingCreationResult createOnlineMeeting(CreateOnlineMeetingCommand command);
}
