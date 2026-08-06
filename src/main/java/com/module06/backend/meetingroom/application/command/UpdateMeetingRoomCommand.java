package com.module06.backend.meetingroom.application.command;

import java.time.LocalTime;

/*
 * ROOM-04 회의실 부분 수정에 필요한 인증·경로·필드 존재 여부를 묶은 애플리케이션 명령이다.
 *
 * PATCH는 값이 없는 것과 필드를 보내지 않은 것이 다르므로 각 값과 provided 플래그를 함께 보관한다.
 * 특히 locationProvided가 true이면서 location이 null이면 기존 위치를 삭제한다.
 */
public record UpdateMeetingRoomCommand(
        Long companyId,
        String requesterRole,
        Long meetingRoomId,
        boolean nameProvided,
        String name,
        boolean locationProvided,
        String location,
        boolean capacityProvided,
        Integer capacity,
        boolean availableFromProvided,
        LocalTime availableFrom,
        boolean availableToProvided,
        LocalTime availableTo
) {

    /* 수정할 속성이 하나라도 요청에 포함됐는지 확인한다. */
    public boolean hasAnyChange() {
        /* 다섯 PATCH 필드 중 하나 이상의 존재 플래그가 true여야 한다. */
        return nameProvided
                || locationProvided
                || capacityProvided
                || availableFromProvided
                || availableToProvided;
    }
}
