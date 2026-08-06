package com.module06.backend.meeting.application.query;

/*
 * CAP-10 현재 캡처 세션 조회에 필요한 인증 정보와 Path 회의 식별자를 묶은 조건이다.
 */
public record GetCaptureSessionQuery(
        Long companyId,
        Long requesterMemberId,
        Long meetingId
) {
}
