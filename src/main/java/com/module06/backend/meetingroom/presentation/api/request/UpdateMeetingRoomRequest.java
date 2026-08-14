package com.module06.backend.meetingroom.presentation.api.request;

import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonSetter;

import com.module06.backend.meetingroom.application.command.UpdateMeetingRoomCommand;

/*
 * ROOM-04 부분 수정 요청 본문이며 각 Setter 호출 여부로 JSON 필드의 미전달과 명시적 null을 구분한다.
 */
public class UpdateMeetingRoomRequest {

    /* 부분 수정할 회의실 이름이며 null 전달은 서비스에서 입력 오류로 처리한다. */
    @Size(max = 100)
    private String name;

    /* JSON에 name 키가 실제로 포함됐는지 기록한다. */
    private boolean nameProvided;

    /* 부분 수정할 위치이며 명시적 null은 기존 위치 삭제를 뜻한다. */
    @Size(max = 150)
    private String location;

    /* JSON에 location 키가 실제로 포함됐는지 기록한다. */
    private boolean locationProvided;

    /* JSON name 필드의 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("name")
    public void setName(String name) {
        /* null 포함 전달값을 그대로 두고 서비스가 필수 이름 규칙을 판정하게 한다. */
        this.name = name;
        this.nameProvided = true;
    }

    /* JSON location 필드의 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("location")
    public void setLocation(String location) {
        /* 명시적 null도 삭제 명령이므로 값과 존재 플래그를 모두 보존한다. */
        this.location = location;
        this.locationProvided = true;
    }

    /* 인증·경로 값과 PATCH 필드 존재 정보를 애플리케이션 명령으로 변환한다. */
    public UpdateMeetingRoomCommand toCommand(Long companyId, String requesterRole, Long meetingRoomId) {
        /* 이름과 위치의 필드 존재 정보를 그대로 보존한다. */
        return new UpdateMeetingRoomCommand(
                companyId,
                requesterRole,
                meetingRoomId,
                nameProvided,
                name,
                locationProvided,
                location
        );
    }
}
