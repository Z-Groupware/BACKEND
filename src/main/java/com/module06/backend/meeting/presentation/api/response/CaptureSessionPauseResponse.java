package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.module06.backend.meeting.application.result.CaptureSessionPauseResult;

/*
 * CAP-02 캡처 일시정지 성공 응답이다.
 */
public record CaptureSessionPauseResponse(
        Long captureSessionId,
        String status,
        boolean isPaused,
        String pausedAt
) {

    /* API가 고정한 초 단위 KST 로컬 일시 형식이다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 애플리케이션 결과를 PAUSED 상태와 초 단위 일시 응답으로 변환한다. */
    public static CaptureSessionPauseResponse from(CaptureSessionPauseResult result) {
        /* 정상 일시정지 결과의 필수 pausedAt을 명세 문자열로 직렬화한다. */
        return new CaptureSessionPauseResponse(
                result.captureSessionId(),
                result.status().name(),
                result.isPaused(),
                formatDateTime(result.pausedAt())
        );
    }

    /* 필수 일시정지 일시를 명세의 오프셋 없는 KST 문자열로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* null 계약 위반을 JSON으로 숨기지 않고 즉시 드러낸다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
