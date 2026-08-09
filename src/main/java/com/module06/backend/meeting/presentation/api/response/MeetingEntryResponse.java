package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.module06.backend.meeting.application.result.MeetingEntryResult;

/*
 * MEET-07 성공 응답의 data 영역이며 입장 후 회의 상태와 화면 제어 정보를 반환한다.
 */
public record MeetingEntryResponse(
        Long meetingId,
        String status,
        String startedAt,
        int attendeeCount,
        boolean recordingConsent,
        boolean isHost,
        boolean canControlRecording
) {

    /* API가 고정한 초 단위 KST 로컬 일시 형식이다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 애플리케이션 입장 결과를 명세의 문자열 상태와 일시 응답으로 변환한다. */
    public static MeetingEntryResponse from(MeetingEntryResult result) {
        /* 최초 시작 시각은 정상 입장 결과에서 필수이며 초 단위 고정 형식으로 직렬화한다. */
        return new MeetingEntryResponse(
                result.meetingId(),
                result.status().name(),
                formatDateTime(result.startedAt()),
                result.attendeeCount(),
                result.recordingConsent(),
                result.isHost(),
                result.canControlRecording()
        );
    }

    /* 필수 시작 일시를 명세 문자열로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* null 상태를 정상 JSON으로 숨기지 않고 데이터 계약 위반으로 즉시 드러낸다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }
}
