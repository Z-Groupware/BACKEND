package com.module06.backend.meeting.application.query;

/*
 * RESULT-01 회의 참석자 조회 조건이다.
 *
 * 회사와 요청자 권한은 Path나 Query Parameter가 아니라 인증 principal에서만 채운다.
 */
public record GetMeetingAttendeesQuery(
        Long companyId,
        Long requesterMemberId,
        String requesterRole,
        boolean requesterAdmin,
        Long meetingId
) {
}
