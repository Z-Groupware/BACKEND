package com.module06.backend.meetingroom.application.command;

/*
 * ROOM-05 회의실 비활성화에 필요한 인증 회사·역할·대상 식별자를 묶은 애플리케이션 명령이다.
 */
public record DeactivateMeetingRoomCommand(
        Long companyId,
        String requesterRole,
        Long meetingRoomId
) {
}
