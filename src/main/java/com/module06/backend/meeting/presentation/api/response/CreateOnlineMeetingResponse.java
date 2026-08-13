package com.module06.backend.meeting.presentation.api.response;

import java.util.List;

import com.module06.backend.meeting.application.result.OnlineMeetingCreationResult;

/*
 * MEET-18 비대면 회의 개설 성공 응답의 data 영역이다.
 *
 * 회의실 예약이 없으므로 MEET-01의 CreateMeetingResponse와 달리 meetingRoom·startAt·endAt이 없고
 * 온라인 여부를 확인할 수 있도록 isOnline을 고정값 true로 내려준다.
 */
public record CreateOnlineMeetingResponse(
        /* 새로 생성된 회의 식별자다. */
        Long meetingId,

        /* 개설 직후의 회의 상태이며 항상 SCHEDULED다. */
        String status,

        /* 저장된 회의 제목이다. */
        String title,

        /* 회의실 예약 없이 개설된 비대면 회의임을 나타내며 항상 true다. */
        boolean isOnline,

        /* 녹음 동의 안내 확인 여부다. */
        boolean recordingConsent,

        /* 인증된 회의 개설자 표시 정보다. */
        HostResponse host,

        /* 개설자가 첫 번째로 포함된 전체 참석자 목록이다. */
        List<AttendeeResponse> attendees
) {

    /* 응답의 참석자 목록을 생성 이후 변경하지 못하도록 불변 복사한다. */
    public CreateOnlineMeetingResponse {
        /* 정상 결과는 항상 목록을 가지므로 null을 숨기지 않고 즉시 실패하게 한다. */
        attendees = List.copyOf(attendees);
    }

    /* 애플리케이션 결과를 외부 API 응답 계약으로 변환한다. */
    public static CreateOnlineMeetingResponse from(OnlineMeetingCreationResult result) {
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

        /* 외부에 필요한 값만 포함한 MEET-18 응답을 반환한다. */
        return new CreateOnlineMeetingResponse(
                result.meetingId(),
                result.status().name(),
                result.title(),
                true,
                result.recordingConsent(),
                host,
                attendees
        );
    }

    /* 회의 개설자의 식별자와 이름을 전달하는 중첩 응답이다. */
    public record HostResponse(Long memberId, String name) {
    }

    /* 참석자의 식별자, 이름, 소속 팀 이름을 전달하는 중첩 응답이다. */
    public record AttendeeResponse(Long memberId, String name, String teamName) {
    }
}
