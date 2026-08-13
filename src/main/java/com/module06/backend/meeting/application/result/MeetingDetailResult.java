package com.module06.backend.meeting.application.result;

import java.time.LocalDateTime;
import java.util.List;

import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.domain.model.MeetingSummaryStatus;

/*
 * MEET-04 애플리케이션 계층이 프레젠테이션 계층에 전달하는 회의 상세 결과다.
 */
public record MeetingDetailResult(
        Long meetingId,
        String title,
        MeetingStatus status,
        LocalDateTime startAt,
        LocalDateTime endAt,
        LocalDateTime startedAt,
        LocalDateTime endedAt,
        boolean recordingConsent,
        long pendingActionCount,
        MeetingSummaryStatus summaryStatus,
        Long teamId,
        String originLabel,
        Agenda agenda,
        Project project,
        MeetingRoom meetingRoom,
        Host host,
        List<Attendee> attendees,
        LocalDateTime createdAt
) {

    /* 상세 결과를 만든 뒤 참석자 목록을 변경할 수 없도록 불변 복사한다. */
    public MeetingDetailResult {
        /* 정상 상세 결과는 항상 참석자 목록을 제공하므로 null을 조용히 숨기지 않는다. */
        attendees = List.copyOf(attendees);
    }

    /* 프로젝트 도메인에서 조회한 회의 연결 프로젝트 표시 정보다. */
    public record Project(Long projectId, String tag, String name, String color) {
    }

    /* 회의실 도메인에서 조회한 과거 이력을 포함한 회의실 표시 정보다. */
    public record MeetingRoom(Long meetingRoomId, String name, String location) {
    }

    /* 회의 개설자를 별도로 표시하기 위한 최소 구성원 정보다. */
    public record Host(Long memberId, String name) {
    }

    /* 상세 화면의 참석자 명단에 표시할 구성원 정보다. */
    public record Attendee(
            Long memberId,
            String name,
            String teamName,
            String jobPosition
    ) {
    }

    public record Agenda(String mainTopic, List<String> subTopics) {
        public Agenda {
            subTopics = List.copyOf(subTopics);
        }
    }
}
