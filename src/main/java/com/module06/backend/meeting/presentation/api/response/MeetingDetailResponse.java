package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.module06.backend.meeting.application.result.MeetingDetailResult;

/*
 * MEET-04 성공 응답의 data 영역으로 회의 메타와 연결 리소스 표시 정보를 제공한다.
 */
public record MeetingDetailResponse(
        Long meetingId,
        String title,
        String status,
        String startAt,
        String endAt,
        String startedAt,
        String endedAt,
        boolean recordingConsent,
        ProjectResponse project,
        MeetingRoomResponse meetingRoom,
        HostResponse host,
        List<AttendeeResponse> attendees,
        String createdAt
) {

    /* API 명세가 요구하는 오프셋 없는 초 단위 KST 일시 형식이다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 응답 생성 이후 참석자 배열을 외부에서 변경할 수 없도록 불변 복사한다. */
    public MeetingDetailResponse {
        /* 정상 결과는 항상 참석자 목록을 제공하므로 null 대신 실제 목록을 복사한다. */
        attendees = List.copyOf(attendees);
    }

    /* 애플리케이션 결과를 MEET-04 외부 JSON 계약으로 변환한다. */
    public static MeetingDetailResponse from(MeetingDetailResult result) {
        /* 중첩 결과와 nullable 실제 시작·종료 시각을 외부 표시 형식으로 조립한다. */
        return new MeetingDetailResponse(
                result.meetingId(),
                result.title(),
                result.status().name(),
                formatDateTime(result.startAt()),
                formatDateTime(result.endAt()),
                formatDateTime(result.startedAt()),
                formatDateTime(result.endedAt()),
                result.recordingConsent(),
                new ProjectResponse(
                        result.project().projectId(),
                        result.project().tag(),
                        result.project().name(),
                        result.project().color()
                ),
                new MeetingRoomResponse(
                        result.meetingRoom().meetingRoomId(),
                        result.meetingRoom().name(),
                        result.meetingRoom().location()
                ),
                new HostResponse(result.host().memberId(), result.host().name()),
                result.attendees().stream()
                        .map(attendee -> new AttendeeResponse(
                                attendee.memberId(),
                                attendee.name(),
                                attendee.teamName(),
                                attendee.jobPosition()
                        ))
                        .toList(),
                formatDateTime(result.createdAt())
        );
    }

    /* nullable 로컬 일시를 명세의 고정 문자열 형식으로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* 예약 상태의 실제 시작·종료 시각처럼 값이 없으면 JSON null을 유지한다. */
        return dateTime == null ? null : dateTime.format(DATE_TIME_FORMATTER);
    }

    /* 회의 상세에 표시할 프로젝트 식별자와 태그·이름·색상이다. */
    public record ProjectResponse(Long projectId, String tag, String name, String color) {
    }

    /* 비활성화된 과거 회의실까지 표시할 회의실 식별자·이름·위치다. */
    public record MeetingRoomResponse(Long meetingRoomId, String name, String location) {
    }

    /* 상세 상단에 표시할 회의 개설자의 식별자와 이름이다. */
    public record HostResponse(Long memberId, String name) {
    }

    /* 회의 참석자 명단에 표시할 이름·팀·직급 정보다. */
    public record AttendeeResponse(
            Long memberId,
            String name,
            String teamName,
            String jobPosition
    ) {
    }
}
