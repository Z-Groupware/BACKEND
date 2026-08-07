package com.module06.backend.meeting.presentation.api.response;

import java.time.format.DateTimeFormatter;

import com.module06.backend.meeting.application.result.MeetingUpdateResult;

/*
 * MEET-05 수정 성공 응답의 data 영역이다.
 */
public record UpdateMeetingResponse(
        Long meetingId,
        String status,
        String startAt,
        String endAt,
        MeetingRoomResponse meetingRoom
) {

    /* API 공통 오프셋 없는 KST 초 단위 일시 포맷터다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 애플리케이션 결과를 MEET-05 외부 JSON 계약으로 변환한다. */
    public static UpdateMeetingResponse from(MeetingUpdateResult result) {
        /* enum과 LocalDateTime을 외부 문자열 계약으로 고정하고 회의실 중첩 응답을 조립한다. */
        return new UpdateMeetingResponse(
                result.meetingId(),
                result.status().name(),
                result.startAt().format(DATE_TIME_FORMATTER),
                result.endAt().format(DATE_TIME_FORMATTER),
                new MeetingRoomResponse(
                        result.meetingRoom().meetingRoomId(),
                        result.meetingRoom().name()
                )
        );
    }

    /* 수정 후 회의실의 식별자와 표시 이름을 제공하는 중첩 응답이다. */
    public record MeetingRoomResponse(Long meetingRoomId, String name) {
    }
}
