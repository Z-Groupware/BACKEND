package com.module06.backend.meeting.application.command;

/* MEET-06 회의 취소에 필요한 인증 주체와 대상 회의 식별자를 전달한다. */
public record CancelMeetingCommand(
        Long companyId,
        Long requesterMemberId,
        String requesterRole,
        boolean requesterAdmin,
        Long meetingId
) {
}
