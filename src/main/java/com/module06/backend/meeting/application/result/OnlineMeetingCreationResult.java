package com.module06.backend.meeting.application.result;

import java.util.List;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-18 저장 결과를 프레젠테이션 계층에 전달하는 애플리케이션 결과 객체다.
 *
 * 회의실 예약이 없으므로 MEET-01의 MeetingCreationResult와 달리 meetingRoom·startAt·endAt을 갖지 않는다.
 */
public record OnlineMeetingCreationResult(
        Long meetingId,
        MeetingStatus status,
        String title,
        boolean recordingConsent,
        Host host,
        List<Attendee> attendees
) {

    /* 참석자 목록을 응답 생성 이후 변경하지 못하도록 불변 복사한다. */
    public OnlineMeetingCreationResult {
        attendees = List.copyOf(attendees);
    }

    /* 개설자 응답에 필요한 식별자와 이름이다. */
    public record Host(Long memberId, String name) {
    }

    /* 참석자 응답에 필요한 식별자와 구성원 표시 정보다. */
    public record Attendee(Long memberId, String name, String teamName) {
    }
}
