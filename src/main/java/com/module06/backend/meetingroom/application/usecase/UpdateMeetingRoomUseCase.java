package com.module06.backend.meetingroom.application.usecase;

import com.module06.backend.meetingroom.application.command.UpdateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.result.MeetingRoomUpdateResult;

/*
 * ROOM-04 회의실 정보 수정 API와 애플리케이션 서비스 사이의 인바운드 포트다.
 */
public interface UpdateMeetingRoomUseCase {

    /* 인증 회사의 활성 회의실을 부분 수정하고 저장된 전체 상태를 반환한다. */
    MeetingRoomUpdateResult updateMeetingRoom(UpdateMeetingRoomCommand command);
}
