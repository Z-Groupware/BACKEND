package com.module06.backend.meetingroom.presentation.api.response;

import com.module06.backend.meetingroom.application.result.MeetingRoomCreationResult;

/*
 * ROOM-03 성공 응답의 data 영역이며 생성된 회의실 식별자만 공개한다.
 */
public record CreateMeetingRoomResponse(Long meetingRoomId) {

    /* 애플리케이션 등록 결과를 외부 API 응답으로 변환한다. */
    public static CreateMeetingRoomResponse from(MeetingRoomCreationResult result) {
        /* 도메인 객체를 노출하지 않고 생성 식별자만 복사한다. */
        return new CreateMeetingRoomResponse(result.meetingRoomId());
    }
}
