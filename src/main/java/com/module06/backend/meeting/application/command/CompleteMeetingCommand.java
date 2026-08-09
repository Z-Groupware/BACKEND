package com.module06.backend.meeting.application.command;

/* MEET-08 회의 종료에 필요한 인증 주체와 대상 회의 식별자를 전달한다. */
public record CompleteMeetingCommand(
        Long companyId,
        Long requesterMemberId,
        String requesterRole,
        boolean requesterAdmin,
        Long meetingId
) {
}
