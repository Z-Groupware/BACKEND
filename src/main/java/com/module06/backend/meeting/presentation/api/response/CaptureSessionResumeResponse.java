package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.module06.backend.meeting.application.result.CaptureSessionResumeResult;

/*
 * CAP-03 캡처 재개 성공 응답이다.
 */
public record CaptureSessionResumeResponse(
        Long captureSessionId,
        String status,
        boolean isPaused,
        String resumedAt
) {

    /* API가 고정한 초 단위 KST 로컬 일시 형식이다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 애플리케이션 결과를 ACTIVE 상태와 초 단위 재개 일시 응답으로 변환한다. */
    public static CaptureSessionResumeResponse from(CaptureSessionResumeResult result) {
        /* 정상 재개 결과의 필수 resumedAt을 명세 문자열로 직렬화한다. */
        return new CaptureSessionResumeResponse(
                result.captureSessionId(),
                result.status().name(),
                result.isPaused(),
                formatDateTime(result.resumedAt())
        );
    }

    /* 필수 재개 일시를 명세의 오프셋 없는 KST 문자열로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* null 계약 위반을 JSON으로 숨기지 않고 즉시 드러낸다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
