package com.module06.backend.meeting.application.command;

/*
 * CAP-01 캡처 세션 시작에 필요한 인증 회사·구성원과 Path 회의 식별자를 묶은 명령이다.
 */
public record StartCaptureSessionCommand(
        Long companyId,
        Long requesterMemberId,
        Long meetingId
) {
}
