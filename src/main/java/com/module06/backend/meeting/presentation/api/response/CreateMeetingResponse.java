package com.module06.backend.meeting.presentation.api.response;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import com.module06.backend.meeting.application.result.MeetingCreationResult;

/*
 * MEET-01 회의 예약 성공 응답의 data 영역이다.
 *
 * 날짜 문자열을 명세의 YYYY-MM-DDTHH:mm:ss 형식으로 직접 변환해 Jackson 전역 설정과 무관하게 계약을 지킨다.
 */
public record CreateMeetingResponse(
        /* 새로 생성된 회의 식별자다. */
        Long meetingId,

        /* 예약 직후의 회의 상태이며 항상 SCHEDULED다. */
        String status,

        /* 저장된 회의 제목이다. */
        String title,

        /* KST 기준 회의 시작 일시 문자열이다. */
        String startAt,

        /* KST 기준 회의 종료 일시 문자열이다. */
        String endAt,

        /* 녹음 동의 안내 확인 여부다. */
        boolean recordingConsent,

        /* 예약된 회의실 표시 정보다. */
        MeetingRoomResponse meetingRoom,

        /* 인증된 회의 개설자 표시 정보다. */
        HostResponse host,

        /* 개설자가 첫 번째로 포함된 전체 참석자 목록이다. */
        List<AttendeeResponse> attendees
) {

    /* API가 고정한 초 단위 로컬 일시 포맷터다. */
    private static final DateTimeFormatter DATE_TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /* 응답의 참석자 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public CreateMeetingResponse {
        /* 정상 결과는 항상 목록을 가지므로 null을 숨기지 않고 즉시 실패하게 한다. */
        attendees = List.copyOf(attendees);
    }

    /* 애플리케이션 결과를 외부 API 응답 계약으로 변환한다. */
    public static CreateMeetingResponse from(MeetingCreationResult result) {
        /* 회의실 읽기 모델을 중첩 API 응답으로 변환한다. */
        MeetingRoomResponse meetingRoom = new MeetingRoomResponse(
                result.meetingRoom().meetingRoomId(),
                result.meetingRoom().name(),
                result.meetingRoom().location()
        );

        /* 개설자 읽기 모델을 중첩 API 응답으로 변환한다. */
        HostResponse host = new HostResponse(
                result.host().memberId(),
                result.host().name()
        );

        /* 애플리케이션이 보존한 개설자 우선 순서로 참석자 응답을 만든다. */
        List<AttendeeResponse> attendees = result.attendees().stream()
                .map(attendee -> new AttendeeResponse(
                        attendee.memberId(),
                        attendee.name(),
                        attendee.teamName()
                ))
                .toList();

        /* 외부에 필요한 필드만 포함한 MEET-01 응답을 반환한다. */
        return new CreateMeetingResponse(
                result.meetingId(),
                result.status().name(),
                result.title(),
                formatDateTime(result.startAt()),
                formatDateTime(result.endAt()),
                result.recordingConsent(),
                meetingRoom,
                host,
                attendees
        );
    }

    /* 필수 로컬 일시를 명세의 초 단위 문자열로 변환한다. */
    private static String formatDateTime(LocalDateTime dateTime) {
        /* null 결과가 정상 JSON으로 숨겨지지 않도록 포맷터가 명확히 실패하게 둔다. */
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /* 회의실 식별자와 화면 표시 값을 전달하는 중첩 응답이다. */
    public record MeetingRoomResponse(Long meetingRoomId, String name, String location) {
    }

    /* 회의 개설자의 식별자와 이름을 전달하는 중첩 응답이다. */
    public record HostResponse(Long memberId, String name) {
    }

    /* 참석자의 식별자, 이름, 소속 팀 이름을 전달하는 중첩 응답이다. */
    public record AttendeeResponse(Long memberId, String name, String teamName) {
    }
}
