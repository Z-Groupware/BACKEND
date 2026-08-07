package com.module06.backend.meeting.application.query;

/*
 * MEET-04 회의 상세 조회에 필요한 인증 범위와 대상 식별자를 묶은 조회 조건이다.
 */
public record GetMeetingDetailQuery(
        Long companyId,
        Long requesterMemberId,
        Long requesterTeamId,
        String requesterRole,
        boolean requesterAdmin,
        Long meetingId
) {
}
