package com.module06.backend.meeting.application.command;

/*
 * CAP-03 캡처 재개에 필요한 인증 회사·구성원과 Path 회의 식별자를 묶은 명령이다.
 */
public record ResumeCaptureSessionCommand(
        Long companyId,
        Long requesterMemberId,
        Long meetingId
) {
}
