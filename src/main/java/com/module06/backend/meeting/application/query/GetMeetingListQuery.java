package com.module06.backend.meeting.application.query;

import java.time.LocalDate;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-02 회의 목록 조회에 필요한 인증 범위와 선택 필터를 묶은 Query다.
 *
 * 회사와 구성원 식별자 및 회사 전체 열람 여부는 Access Token의 principal에서만 만들고
 * 외부 요청 파라미터로 받지 않아 테넌트와 권한 범위를 변경할 수 없게 한다.
 */
public record GetMeetingListQuery(
        Long companyId,
        Long requesterMemberId,
        boolean companyWideRead,
        Long projectId,
        Long meetingRoomId,
        LocalDate from,
        LocalDate to,
        MeetingStatus status,
        Integer page,
        Integer size
) {
}
