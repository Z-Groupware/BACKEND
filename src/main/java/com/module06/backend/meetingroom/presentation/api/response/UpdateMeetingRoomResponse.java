package com.module06.backend.meetingroom.presentation.api.response;

import com.module06.backend.meetingroom.application.result.MeetingRoomUpdateResult;

/*
 * ROOM-04 성공 응답의 data 영역이며 수정 후 회의실 전체 표시 정보를 반환한다.
 */
public record UpdateMeetingRoomResponse(
        Long meetingRoomId,
        String name,
        String location
) {

    /* 애플리케이션 수정 결과를 외부 응답으로 변환한다. */
    public static UpdateMeetingRoomResponse from(MeetingRoomUpdateResult result) {
        /* 모든 회의실은 24시간 운영하므로 변경 가능한 표시 정보만 반환한다. */
        return new UpdateMeetingRoomResponse(
                result.meetingRoomId(),
                result.name(),
                result.location()
        );
    }
}
