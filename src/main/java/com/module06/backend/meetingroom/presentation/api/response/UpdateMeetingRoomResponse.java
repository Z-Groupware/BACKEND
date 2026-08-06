package com.module06.backend.meetingroom.presentation.api.response;

import com.module06.backend.meetingroom.application.result.MeetingRoomUpdateResult;

/*
 * ROOM-04 성공 응답의 data 영역이며 수정 후 회의실 전체 표시 정보를 반환한다.
 */
public record UpdateMeetingRoomResponse(
        Long meetingRoomId,
        String name,
        String location,
        int capacity,
        String availableFrom,
        String availableTo
) {

    /* 애플리케이션 수정 결과를 외부 시각 문자열 계약에 맞는 응답으로 변환한다. */
    public static UpdateMeetingRoomResponse from(MeetingRoomUpdateResult result) {
        /* LocalTime은 회의실 API 공통 HH:mm 포맷터를 사용해 문자열로 변환한다. */
        return new UpdateMeetingRoomResponse(
                result.meetingRoomId(),
                result.name(),
                result.location(),
                result.capacity(),
                ApiTimeFormat.formatTime(result.availableFrom()),
                ApiTimeFormat.formatTime(result.availableTo())
        );
    }
}
