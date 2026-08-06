package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;

import com.module06.backend.meeting.domain.model.CaptureSessionStatus;

/*
 * CAP-02 일시정지 완료 후 프레젠테이션 계층에 전달할 세션 상태와 시각이다.
 */
public record CaptureSessionPauseResult(
        Long captureSessionId,
        CaptureSessionStatus status,
        boolean isPaused,
        LocalDateTime pausedAt
) {
}
