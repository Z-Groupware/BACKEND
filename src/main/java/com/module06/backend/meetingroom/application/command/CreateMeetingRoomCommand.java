package com.module06.backend.meetingroom.application.command;

import java.time.LocalTime;

/*
 * ROOM-03 회의실 등록에 필요한 인증 회사와 요청 속성을 묶은 애플리케이션 명령이다.
 *
 * companyId는 요청 본문이 아니라 Access Token principal에서만 전달받는다.
 */
public record CreateMeetingRoomCommand(
        Long companyId,
        String name,
        String location,
        LocalTime availableFrom,
        LocalTime availableTo
) {
}
