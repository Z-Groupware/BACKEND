package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;

import com.module06.backend.meeting.domain.model.MeetingStatus;

/*
 * MEET-07 입장 완료 후 프레젠테이션 계층에 전달할 회의 상태와 화면 분기 정보다.
 */
public record MeetingEntryResult(
        Long meetingId,
        MeetingStatus status,
        LocalDateTime startedAt,
        int attendeeCount,
        boolean recordingConsent,
        boolean isHost,
        boolean canControlRecording
) {
}
