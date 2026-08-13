package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.module06.backend.meeting.application.result.DashboardMeetingListResult;

/*
 * MEET-17 성공 응답의 data 영역이다.
 *
 * 조회 결과가 없을 때도 meetings를 null이 아닌 빈 배열로 직렬화한다.
 */
public record DashboardMeetingListResponse(List<MeetingResponse> meetings) {

    /* API가 고정한 초 단위 KST 로컬 일시 형식이다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 대시보드 최근 회의 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public DashboardMeetingListResponse {
        meetings = List.copyOf(meetings);
    }

    /* 애플리케이션 결과를 MEET-17 외부 응답 계약으로 변환한다. */
    public static DashboardMeetingListResponse from(DashboardMeetingListResult result) {
        /* 저장소가 보장한 최근 순서를 유지하면서 각 회의를 중첩 응답으로 변환한다. */
        List<MeetingResponse> meetings = result.meetings().stream()
                .map(DashboardMeetingListResponse::toMeetingResponse)
                .toList();
        return new DashboardMeetingListResponse(meetings);
    }

    /* 대시보드 최근 회의 애플리케이션 결과 한 건을 외부 카드 응답으로 변환한다. */
    private static MeetingResponse toMeetingResponse(DashboardMeetingListResult.MeetingItem meeting) {
        return new MeetingResponse(
                meeting.meetingId(),
                meeting.title(),
                meeting.projectTag(),
                meeting.status().name(),
                meeting.room(),
                formatDateTime(meeting.scheduledAt()),
                meeting.attendeeCount(),
                meeting.originLabel(),
                meeting.hostLabel()
        );
    }

    /* 필수 로컬 일시를 명세의 초 단위 문자열로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /* 대시보드의 최근 회의 카드 한 건을 나타내는 응답이다. */
    public record MeetingResponse(
            Long meetingId,
            String title,
            String projectTag,
            String status,
            String room,
            String scheduledAt,
            int attendeeCount,
            String originLabel,
            String hostLabel
    ) {
    }
}
