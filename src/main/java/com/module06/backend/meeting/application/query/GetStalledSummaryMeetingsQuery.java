package com.module06.backend.meeting.application.query;

import java.time.LocalDate;

/*
 * MEET-15 조회에 필요한 인증 범위와 선택 필터 및 페이지 값을 묶은 Query다.
 *
 * 회사와 구성원 식별자는 인증 principal에서만 만들고 외부 파라미터로 받지 않는다.
 */
public record GetStalledSummaryMeetingsQuery(
        Long companyId,
        Long requesterMemberId,
        Long projectId,
        LocalDate from,
        LocalDate to,
        Integer page,
        Integer size
) {
}
