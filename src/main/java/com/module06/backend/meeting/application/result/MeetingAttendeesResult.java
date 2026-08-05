package com.module06.backend.meeting.application.result;

import java.util.List;

/*
 * RESULT-01 참석자 조회 결과를 프레젠테이션 계층에 전달하는 애플리케이션 결과 객체다.
 */
public record MeetingAttendeesResult(
        Long meetingId,
        List<Attendee> attendees
) {

    /* 참석자 결과 목록이 생성 이후 변경되지 않도록 불변 복사한다. */
    public MeetingAttendeesResult {
        /* 정상 조회는 항상 목록을 제공하므로 null을 숨기지 않고 즉시 실패하게 한다. */
        attendees = List.copyOf(attendees);
    }

    /* 참석자 한 명의 표시 정보와 개설자 여부다. */
    public record Attendee(
            Long memberId,
            String name,
            String teamName,
            boolean host
    ) {
    }
}
