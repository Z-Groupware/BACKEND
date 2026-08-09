package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;

import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.domain.model.MeetingStatus;

/* MEET-08이 반환할 저장 완료 상태와 비동기 분석 접수 상태다. */
public record MeetingCompletionResult(
        Long meetingId,
        MeetingStatus meetingStatus,
        String processingStatus,
        CaptureSessionStatus captureSessionStatus,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        long durationMinutes
) {
}
