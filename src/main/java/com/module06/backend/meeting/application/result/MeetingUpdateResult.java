package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-05 수정 완료 후 프레젠테이션 계층에 전달하는 애플리케이션 결과다.
 */
public record MeetingUpdateResult(
        Long meetingId,
        MeetingStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        MeetingRoom meetingRoom
) {

    /* 수정 후 회의실의 식별자와 표시 이름이다. */
    public record MeetingRoom(Long meetingRoomId, String name) {
    }
}
