package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.module06.backend.meeting.application.result.CaptureSessionStateResult;

/*
 * CAP-10 현재 캡처 세션 조회 성공 응답이다.
 */
public record CaptureSessionStateResponse(
        Long captureSessionId,
        String status,
        boolean isPaused,
        long startedAtEpochMs,
        String startedAt,
        String pausedAt
) {

    /* API가 고정한 초 단위 KST 로컬 일시 형식이다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 애플리케이션 결과를 새로고침 복구에 필요한 공개 상태 응답으로 변환한다. */
    public static CaptureSessionStateResponse from(CaptureSessionStateResult result) {
        /* pausedAt만 상태에 따라 null일 수 있고 시작 시간축은 항상 반환한다. */
        return new CaptureSessionStateResponse(
                result.captureSessionId(),
                result.status().name(),
                result.isPaused(),
                result.startedAtEpochMs(),
                formatRequiredDateTime(result.startedAt()),
                formatNullableDateTime(result.pausedAt())
        );
    }

    /* 필수 시작 일시의 null 계약 위반을 숨기지 않고 명세 문자열로 변환한다. */
    private static String formatRequiredDateTime(LocalDateTime dateTime) {
        /* 저장 원본이 없으면 즉시 실패해 잘못된 성공 응답을 만들지 않는다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /* 선택 일시가 있으면 명세 문자열로 변환하고 없으면 JSON null을 유지한다. */
    private static String formatNullableDateTime(LocalDateTime dateTime) {
        /* ACTIVE 등 현재 일시정지가 아닌 상태는 pausedAt null을 그대로 반환한다. */
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER);
    }
}
