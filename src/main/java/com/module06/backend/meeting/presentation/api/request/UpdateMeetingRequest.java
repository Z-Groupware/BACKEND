package com.module06.backend.meeting.presentation.api.request;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSetter;

import com.module06.backend.meeting.application.command.UpdateMeetingCommand;

/*
 * MEET-05 부분 수정 요청이며 Setter 호출 여부로 JSON 필드 미전달과 명시적 null을 구분한다.
 *
 * 회사·요청자·권한은 본문에서 받지 않고 인증 principal 값으로만 Command에 결합한다.
 */
public class UpdateMeetingRequest {

    /* 부분 수정할 회의 제목이며 명시적 null·공백은 서비스에서 입력 오류로 처리한다. */
    @Size(max = 200)
    private String title;

    /* JSON 본문에 title 키가 실제로 포함됐는지 기록한다. */
    private boolean titleProvided;

    /* 부분 수정할 프로젝트 식별자다. */
    @Positive
    private Long projectId;

    /* JSON 본문에 projectId 키가 실제로 포함됐는지 기록한다. */
    private boolean projectIdProvided;

    /* 부분 수정할 회의실 식별자다. */
    @Positive
    private Long meetingRoomId;

    /* JSON 본문에 meetingRoomId 키가 실제로 포함됐는지 기록한다. */
    private boolean meetingRoomIdProvided;

    /* 부분 수정할 오프셋 없는 KST 예약 시작 일시다. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime startAt;

    /* JSON 본문에 startAt 키가 실제로 포함됐는지 기록한다. */
    private boolean startAtProvided;

    /* 부분 수정할 오프셋 없는 KST 예약 종료 일시다. */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime endAt;

    /* JSON 본문에 endAt 키가 실제로 포함됐는지 기록한다. */
    private boolean endAtProvided;

    /* 부분 수정할 녹음 동의 안내 여부다. */
    private Boolean recordingConsent;

    /* JSON 본문에 recordingConsent 키가 실제로 포함됐는지 기록한다. */
    private boolean recordingConsentProvided;

    /* JSON title 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("title")
    public void setTitle(String title) {
        /* null 포함 원본을 보존해 서비스가 PATCH 필수값 계약을 판단하게 한다. */
        this.title = title;
        this.titleProvided = true;
    }

    /* JSON projectId 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("projectId")
    public void setProjectId(Long projectId) {
        /* 명시적 null도 미전달과 다르므로 값과 플래그를 모두 보존한다. */
        this.projectId = projectId;
        this.projectIdProvided = true;
    }

    /* JSON meetingRoomId 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("meetingRoomId")
    public void setMeetingRoomId(Long meetingRoomId) {
        /* 서비스가 활성 회의실 조회 전에 null·양수 계약을 검증할 수 있게 한다. */
        this.meetingRoomId = meetingRoomId;
        this.meetingRoomIdProvided = true;
    }

    /* JSON startAt 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("startAt")
    public void setStartAt(LocalDateTime startAt) {
        /* Jackson이 파싱한 KST 로컬 일시와 명시적 null 여부를 그대로 전달한다. */
        this.startAt = startAt;
        this.startAtProvided = true;
    }

    /* JSON endAt 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("endAt")
    public void setEndAt(LocalDateTime endAt) {
        /* 최종 시간 범위 조립을 위해 값과 전달 플래그를 함께 보관한다. */
        this.endAt = endAt;
        this.endAtProvided = true;
    }

    /* JSON recordingConsent 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("recordingConsent")
    public void setRecordingConsent(Boolean recordingConsent) {
        /* false도 유효한 수정값이므로 null 검사 대신 존재 플래그로 전달 여부를 구분한다. */
        this.recordingConsent = recordingConsent;
        this.recordingConsentProvided = true;
    }

    /* 인증·Path 값과 PATCH 필드 존재 정보를 애플리케이션 Command로 변환한다. */
    public UpdateMeetingCommand toCommand(
            Long companyId,
            Long requesterMemberId,
            String requesterRole,
            boolean requesterAdmin,
            Long meetingId
    ) {
        /* 조작 불가능한 principal 값과 요청 본문 원본을 손실 없이 하나의 Command로 묶는다. */
        return new UpdateMeetingCommand(
                companyId,
                requesterMemberId,
                requesterRole,
                requesterAdmin,
                meetingId,
                titleProvided,
                title,
                projectIdProvided,
                projectId,
                meetingRoomIdProvided,
                meetingRoomId,
                startAtProvided,
                startAt,
                endAtProvided,
                endAt,
                recordingConsentProvided,
                recordingConsent
        );
    }
}
