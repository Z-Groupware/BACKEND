package com.module06.backend.meeting.application.query;

/*
 * MEET-03 내 예정 회의 목록 조회에 필요한 인증 식별자와 조회 개수를 묶은 Query다.
 *
 * 회사와 구성원 식별자는 Access Token principal에서만 전달받고 외부 요청 파라미터로 노출하지 않는다.
 */
public record GetUpcomingMeetingsQuery(
        Long companyId,
        Long requesterMemberId,
        Integer limit
) {
}
