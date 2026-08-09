package com.module06.backend.meeting.application.query;

/*
 * MEET-10 확정 대기 회의 목록 조회에 필요한 인증 식별자를 묶은 Query다.
 *
 * 이 화면은 "내가 처리할 일" 목록이라 롤이 아니라 host 본인 여부가 기준이므로,
 * 회사와 구성원 식별자를 Access Token principal에서만 받고 요청 파라미터로 노출하지 않는다.
 */
public record GetPendingActionMeetingsQuery(
        Long companyId,
        Long requesterMemberId
) {
}
