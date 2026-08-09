package com.module06.backend.meeting.application.command;

/*
 * MEET-07 회의 입장에 필요한 인증 회사·구성원과 Path 회의 식별자를 묶은 애플리케이션 명령이다.
 */
public record EnterMeetingCommand(
        Long companyId,
        Long requesterMemberId,
        Long meetingId
) {
}
