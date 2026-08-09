package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.module06.backend.meeting.application.result.MeetingCompletionResult;

/* MEET-08 회의 종료와 비동기 분석 접수 성공 응답이다. */
public record MeetingCompletionResponse(
        Long meetingId,
        String meetingStatus,
        String processingStatus,
        String captureSessionStatus,
        String startedAt,
        String endedAt,
        long durationMinutes
) {

    /* API가 고정한 초 단위 KST 로컬 일시 형식이다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 애플리케이션 종료 결과를 명세의 문자열 상태와 시간 형식으로 변환한다. */
    public static MeetingCompletionResponse from(MeetingCompletionResult result) {
        /* 완료 결과의 필수 상태·시각을 숨김없이 공개 응답 필드에 대응시킨다. */
        return new MeetingCompletionResponse(
                result.meetingId(),
                result.meetingStatus().name(),
                result.processingStatus(),
                result.captureSessionStatus().name(),
                formatRequiredDateTime(result.startedAt()),
                formatRequiredDateTime(result.endedAt()),
                result.durationMinutes()
        );
    }

    /* 완료 응답의 필수 실제 시각을 초 단위 문자열로 변환한다. */
    private static String formatRequiredDateTime(LocalDateTime dateTime) {
        /* null이면 계약 위반을 즉시 드러내 잘못된 성공 응답 생성을 막는다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
