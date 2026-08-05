package com.module06.backend.meetingroom.application.usecase;

import com.module06.backend.meetingroom.application.command.CreateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.result.MeetingRoomCreationResult;

/*
 * ROOM-03 회의실 등록 요청을 애플리케이션 계층에 전달하는 인바운드 포트다.
 */
public interface CreateMeetingRoomUseCase {

    /* 검증된 회사 범위에 활성 회의실을 만들고 생성 식별자를 반환한다. */
    MeetingRoomCreationResult createMeetingRoom(CreateMeetingRoomCommand command);
}
