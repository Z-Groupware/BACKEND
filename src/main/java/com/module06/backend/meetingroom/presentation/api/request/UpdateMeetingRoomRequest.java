package com.module06.backend.meetingroom.presentation.api.request;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.annotation.JsonSetter;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
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

    /* 부분 수정할 최대 수용 인원이며 1 이상이어야 한다. */
    @Positive
    private Integer capacity;

    /* JSON에 capacity 키가 실제로 포함됐는지 기록한다. */
    private boolean capacityProvided;

    /* 부분 수정할 이용 가능 시작 시각 문자열이다. */
    @Pattern(regexp = "^(?:[01]\\d|2[0-3]):(?:00|30)$")
    private String availableFrom;

    /* JSON에 availableFrom 키가 실제로 포함됐는지 기록한다. */
    private boolean availableFromProvided;

    /* 부분 수정할 이용 가능 종료 시각 문자열이다. */
    @Pattern(regexp = "^(?:[01]\\d|2[0-3]):(?:00|30)$")
    private String availableTo;

    /* JSON에 availableTo 키가 실제로 포함됐는지 기록한다. */
    private boolean availableToProvided;

    /* API 계약에서 사용하는 분 단위 24시간제 시각 포맷터다. */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

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

    /* JSON capacity 필드의 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("capacity")
    public void setCapacity(Integer capacity) {
        /* 명시적 null은 서비스 입력 검증에서 거절할 수 있도록 그대로 보존한다. */
        this.capacity = capacity;
        this.capacityProvided = true;
    }

    /* JSON availableFrom 필드의 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("availableFrom")
    public void setAvailableFrom(String availableFrom) {
        /* 형식 검증 전 원본 문자열과 존재 플래그를 기록한다. */
        this.availableFrom = availableFrom;
        this.availableFromProvided = true;
    }

    /* JSON availableTo 필드의 값과 존재 여부를 함께 기록한다. */
    @JsonSetter("availableTo")
    public void setAvailableTo(String availableTo) {
        /* 형식 검증 전 원본 문자열과 존재 플래그를 기록한다. */
        this.availableTo = availableTo;
        this.availableToProvided = true;
    }

    /* 인증·경로 값과 PATCH 필드 존재 정보를 애플리케이션 명령으로 변환한다. */
    public UpdateMeetingRoomCommand toCommand(Long companyId, String requesterRole, Long meetingRoomId) {
        /* 전달된 시각만 LocalTime으로 파싱하고 미전달과 명시적 null은 플래그로 구분한다. */
        return new UpdateMeetingRoomCommand(
                companyId,
                requesterRole,
                meetingRoomId,
                nameProvided,
                name,
                locationProvided,
                location,
                capacityProvided,
                capacity,
                availableFromProvided,
                parseTime(availableFrom, availableFromProvided),
                availableToProvided,
                parseTime(availableTo, availableToProvided)
        );
    }

    /* 전달된 시각 문자열만 LocalTime으로 변환하고 형식 오류를 공통 입력 오류로 처리한다. */
    private LocalTime parseTime(String value, boolean provided) {
        /* 미전달 또는 명시적 null은 서비스가 존재 플래그와 함께 판정하도록 null로 유지한다. */
        if (!provided || value == null) {
            return null;
        }

        /* Bean Validation을 우회한 직접 호출에서도 잘못된 형식을 도메인까지 전달하지 않는다. */
        try {
            return LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
