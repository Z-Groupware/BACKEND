package com.module06.backend.meeting.presentation.api.response;

import com.module06.backend.meeting.application.result.MeetingAttendeesResult;

/*
 * RESULT-01 응답의 참석자 한 명을 표현하는 프레젠테이션 DTO다.
 *
 * personKey는 A도메인이 STT 화자 후보를 식별하는 안정적인 문자열 키이며,
 * 명단 외 화자의 unknown_person 값은 A가 추가하므로 이 응답에는 실제 구성원만 포함한다.
 */
public record MeetingAttendeeResponse(
        String personKey,
        Long memberId,
        String name,
        String teamName,
        String type,
        boolean isHost
) {

    /* 실제 구성원 화자 타입의 고정 계약 값이다. */
    private static final String MEMBER_TYPE = "MEMBER";

    /* 애플리케이션 참석자 결과를 A 연동용 REST 응답 항목으로 변환한다. */
    public static MeetingAttendeeResponse from(MeetingAttendeesResult.Attendee attendee) {
        /* memberId에서 화자 매핑 키를 만들고 실제 구성원 타입을 명시한다. */
        return new MeetingAttendeeResponse(
                "member:" + attendee.memberId(),
                attendee.memberId(),
                attendee.name(),
                attendee.teamName(),
                MEMBER_TYPE,
                attendee.host()
        );
    }
}
