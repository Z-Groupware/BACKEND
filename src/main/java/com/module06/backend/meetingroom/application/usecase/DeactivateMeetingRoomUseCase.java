package com.module06.backend.meetingroom.application.usecase;

import com.module06.backend.meetingroom.application.command.DeactivateMeetingRoomCommand;

/*
 * ROOM-05 회의실 비활성화를 프레젠테이션 계층에 제공하는 인바운드 Port다.
 */
@FunctionalInterface
public interface DeactivateMeetingRoomUseCase {

    /* 인증 회사의 활성 회의실을 미래 예약이 없을 때 소프트 삭제한다. */
    void deactivateMeetingRoom(DeactivateMeetingRoomCommand command);
}
