package com.module06.backend.meeting.presentation.api.request;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;

import com.module06.backend.meeting.application.command.CreateMeetingCommand;

/*
 * MEET-01 회의 예약 요청 본문이다.
 *
 * 회사와 개설자 정보는 조작 가능한 본문에서 받지 않고 인증 principal에서 주입한다.
 * 형식 검증은 프레젠테이션 계층에서 처리하고 시간 순서와 도메인 유효성은 애플리케이션 계층에서 처리한다.
 */
public record CreateMeetingRequest(
        /* 회의 목록과 상세 화면에 표시할 제목이다. */
        @NotBlank @Size(max = 200) String title,

        /* 회의가 소속될 필수 프로젝트 식별자다. */
        @NotNull @Positive Long projectId,

        /* 예약할 활성 회의실 식별자다. */
        @NotNull @Positive Long meetingRoomId,

        /* 오프셋 없는 KST 기준 회의 시작 일시다. */
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime startAt,

        /* 오프셋 없는 KST 기준 회의 종료 일시다. */
        @NotNull @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss") LocalDateTime endAt,

        /* 녹음 동의 안내를 확인했는지 나타내는 선택 값이다. */
        Boolean recordingConsent,

        /* 액션 보드에서 회의를 예약한 경우 연결할 선택 식별자다. */
        @Positive Long relatedActionId,

        /* 개설자를 제외하고 선택한 참석자 식별자 목록이며 최소 한 명이 필요하다. */
        @NotNull @Size(min = 1) List<@NotNull @Positive Long> attendeeMemberIds,

        /* 회의에서 다룰 필수 대주제이며 meeting_topic의 MAIN으로 저장한다. */
        @NotBlank @Size(max = 300) String mainTopic,

        /* 대주제 아래에 표시할 필수 소주제 목록이며 하나 이상이어야 한다. */
        @NotNull @Size(min = 1) List<@NotBlank @Size(max = 300) String> subTopics
) {

    /* 외부에서 전달된 목록을 요청 객체 생성 시점에 불변 복사한다. */
    public CreateMeetingRequest {
        /* null은 Bean Validation이 처리하고 유효한 목록만 방어적으로 복사한다. */
        if (attendeeMemberIds != null) {
            attendeeMemberIds = List.copyOf(attendeeMemberIds);
        }

        /* null은 Bean Validation이 처리하고 유효한 소주제 목록만 방어적으로 복사한다. */
        if (subTopics != null) {
            subTopics = List.copyOf(subTopics);
        }
    }

    /* 인증 정보와 본문을 합쳐 애플리케이션 유스케이스 명령으로 변환한다. */
    public CreateMeetingCommand toCommand(Long companyId, Long hostMemberId, Long hostTeamId, String hostRole) {
        /* 선택값을 보내지 않으면 명세의 기본값인 false를 사용한다. */
        boolean consent = Boolean.TRUE.equals(recordingConsent);

        /* 테넌트와 개설자 식별자는 인증 principal에서만 가져온다. */
        return new CreateMeetingCommand(
                companyId,
                hostMemberId,
                hostTeamId,
                hostRole,
                title,
                projectId,
                meetingRoomId,
                startAt,
                endAt,
                consent,
                relatedActionId,
                attendeeMemberIds,
                mainTopic,
                subTopics
        );
    }
}
