package com.module06.backend.meeting.presentation.api.response;

import java.util.List;

import com.module06.backend.meeting.application.result.MeetingAttendeesResult;

/*
 * RESULT-01 성공 응답의 data 영역이다.
 */
public record MeetingAttendeeListResponse(
        Long meetingId,
        List<MeetingAttendeeResponse> attendees
) {

    /* 응답 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public MeetingAttendeeListResponse {
        /* 정상 결과는 항상 목록을 제공하므로 null을 숨기지 않고 즉시 실패하게 한다. */
        attendees = List.copyOf(attendees);
    }

    /* 애플리케이션 결과를 RESULT-01 외부 응답 계약으로 변환한다. */
    public static MeetingAttendeeListResponse from(MeetingAttendeesResult result) {
        /* 개설자 우선 순서를 유지하면서 각 참석자에 personKey와 타입을 추가한다. */
        List<MeetingAttendeeResponse> attendees = result.attendees().stream()
                .map(MeetingAttendeeResponse::from)
                .toList();

        /* 대상 회의 식별자와 변환된 참석자 목록을 반환한다. */
        return new MeetingAttendeeListResponse(result.meetingId(), attendees);
    }
}
