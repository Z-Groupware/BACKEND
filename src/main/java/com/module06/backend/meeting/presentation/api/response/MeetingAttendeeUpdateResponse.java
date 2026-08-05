package com.module06.backend.meeting.presentation.api.response;

import java.util.List;

import com.module06.backend.meeting.application.result.MeetingAttendeeUpdateResult;

/*
 * MEET-09 성공 응답의 data 영역이다.
 */
public record MeetingAttendeeUpdateResponse(
        Long meetingId,
        List<AttendeeResponse> attendees
) {

    /* 최종 참석자 응답 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public MeetingAttendeeUpdateResponse {
        /* 정상 결과는 항상 목록을 제공하므로 null을 숨기지 않고 즉시 실패하게 한다. */
        attendees = List.copyOf(attendees);
    }

    /* 애플리케이션 교체 결과를 MEET-09 외부 응답 계약으로 변환한다. */
    public static MeetingAttendeeUpdateResponse from(MeetingAttendeeUpdateResult result) {
        /* 서비스가 보장한 개설자 우선 순서를 유지하면서 표시 응답으로 변환한다. */
        List<AttendeeResponse> attendees = result.attendees().stream()
                .map(attendee -> new AttendeeResponse(
                        attendee.memberId(),
                        attendee.name(),
                        attendee.teamName()
                ))
                .toList();

        /* 대상 회의 식별자와 최종 참석자 전체 명단을 반환한다. */
        return new MeetingAttendeeUpdateResponse(result.meetingId(), attendees);
    }

    /* 최종 참석자 한 명의 식별자와 표시 정보를 나타내는 응답이다. */
    public record AttendeeResponse(Long memberId, String name, String teamName) {
    }
}
