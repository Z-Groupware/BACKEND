package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;

import com.module06.backend.meeting.domain.model.CaptureSessionStatus;

/*
 * CAP-10이 프레젠테이션 계층에 전달하는 D 소유 캡처 세션 상태와 시간축이다.
 */
public record CaptureSessionStateResult(
        Long captureSessionId,
        CaptureSessionStatus status,
        boolean isPaused,
        long startedAtEpochMs,
        LocalDateTime startedAt,
        LocalDateTime pausedAt
) {
}
