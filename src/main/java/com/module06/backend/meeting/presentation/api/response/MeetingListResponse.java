package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.module06.backend.meeting.application.result.MeetingListResult;

/*
 * MEET-02 성공 응답의 data 영역이다.
 *
 * 조회 결과가 없어도 meetings와 page를 항상 제공해 프론트가 null 분기 없이 목록을 렌더링한다.
 */
public record MeetingListResponse(
        List<MeetingResponse> meetings,
        PageResponse page
) {

    /* API가 고정한 초 단위 로컬 일시 형식이다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 응답 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public MeetingListResponse {
        /* 빈 목록은 허용하되 null 목록은 외부 계약 위반으로 즉시 실패하게 한다. */
        meetings = List.copyOf(meetings);
    }

    /* 애플리케이션 결과를 명세의 회의 배열과 페이지 응답으로 변환한다. */
    public static MeetingListResponse from(MeetingListResult result) {
        /* 저장소 내림차순을 유지하면서 각 결과 행을 외부 응답으로 변환한다. */
        List<MeetingResponse> meetings = result.meetings().stream()
                .map(MeetingListResponse::toMeetingResponse)
                .toList();

        /* 현재 페이지와 전체 결과 규모를 명세 필드명으로 변환한다. */
        PageResponse page = new PageResponse(
                result.page().page(),
                result.page().size(),
                result.page().totalElements(),
                result.page().totalPages()
        );

        /* 완성된 회의 목록과 페이지 메타데이터를 data 영역으로 반환한다. */
        return new MeetingListResponse(meetings, page);
    }

    /* 애플리케이션 결과 한 건을 회의실·프로젝트·참석자 중첩 응답으로 변환한다. */
    private static MeetingResponse toMeetingResponse(MeetingListResult.MeetingItem meeting) {
        /* enum 상태와 로컬 일시를 외부 문자열 계약으로 변환하고 중첩 표시값을 조립한다. */
        return new MeetingResponse(
                meeting.meetingId(),
                meeting.title(),
                meeting.status().name(),
                meeting.teamId(),
                meeting.originLabel(),
                meeting.summaryStatus() == null ? null : meeting.summaryStatus().name(),
                meeting.isOnline(),
                formatDateTime(meeting.startAt()),
                formatDateTime(meeting.endAt()),
                meeting.attendeeCount(),
                meeting.actionCount(),
                meeting.isHost(),
                meeting.entryAvailable(),
                meeting.durationMinutes(),
                meeting.attendees().stream()
                        .map(attendee -> new AttendeeResponse(attendee.memberId(), attendee.name()))
                        .toList(),
                meeting.agendaPreview() == null ? null : new AgendaPreviewResponse(
                        meeting.agendaPreview().mainTopic(),
                        meeting.agendaPreview().firstSubTopic()
                ),
                meeting.meetingRoom() == null
                        ? null
                        : new MeetingRoomResponse(
                                meeting.meetingRoom().meetingRoomId(),
                                meeting.meetingRoom().name()
                        ),
                new ProjectResponse(
                        meeting.project().projectId(),
                        meeting.project().tag(),
                        meeting.project().name()
                )
        );
    }

    /* 필수 회의 일시를 초 단위 고정 문자열로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* 비대면 회의는 예약 일시가 없으므로 null을 유지하고 대면 회의만 고정 형식으로 변환한다. */
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER);
    }

    /* 회의 목록 한 행의 외부 응답 계약이다. */
    public record MeetingResponse(
            Long meetingId,
            String title,
            String status,
            Long teamId,
            String originLabel,
            String summaryStatus,
            boolean isOnline,
            String startAt,
            String endAt,
            int attendeeCount,
            long actionCount,
            boolean isHost,
            boolean entryAvailable,
            int durationMinutes,
            List<AttendeeResponse> attendees,
            AgendaPreviewResponse agendaPreview,
            MeetingRoomResponse meetingRoom,
            ProjectResponse project
    ) {
    }

    /* 목록 행에 표시할 회의실 식별자와 이름이다. */
    public record MeetingRoomResponse(Long meetingRoomId, String name) {
    }

    /* 목록 행에 표시할 프로젝트 식별자와 태그·이름이다. */
    public record ProjectResponse(Long projectId, String tag, String name) {
    }

    /* 카드 아바타에 표시할 참석자 식별자와 이름이다. */
    public record AttendeeResponse(Long memberId, String name) {
    }

    public record AgendaPreviewResponse(String mainTopic, String firstSubTopic) {
    }

    /* 페이지 이동과 전체 결과 표시를 위한 외부 페이지 메타데이터다. */
    public record PageResponse(int page, int size, long totalElements, int totalPages) {
    }
}
