package com.module06.backend.meetingroom.application.result;

/*
 * ROOM-02 응답에서 주간 조회 대상 회의실의 표시 정보를 표현하는 애플리케이션 결과 객체다.
 *
 * @param meetingRoomId 회의실 식별자
 * @param name 회의실 이름
 */
public record MeetingRoomAvailabilitySummary(
        Long meetingRoomId,
        String name
) {
}
