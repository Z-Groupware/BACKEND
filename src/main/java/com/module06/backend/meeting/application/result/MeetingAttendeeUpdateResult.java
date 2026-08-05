package com.module06.backend.meeting.application.result;

import java.util.List;

/*
 * MEET-09 참석자 전체 교체 결과를 프레젠테이션 계층에 전달하는 객체다.
 */
public record MeetingAttendeeUpdateResult(
        Long meetingId,
        List<Attendee> attendees
) {

    /* 교체된 참석자 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public MeetingAttendeeUpdateResult {
        /* 정상 결과는 항상 목록을 제공하므로 null을 숨기지 않고 즉시 실패하게 한다. */
        attendees = List.copyOf(attendees);
    }

    /* 최종 참석자 한 명의 식별자와 구성원 표시 정보다. */
    public record Attendee(Long memberId, String name, String teamName) {
    }
}
