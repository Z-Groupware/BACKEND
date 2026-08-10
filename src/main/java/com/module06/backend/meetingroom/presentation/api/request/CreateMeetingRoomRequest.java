package com.module06.backend.meetingroom.presentation.api.request;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.module06.backend.global.exception.BusinessException;
import com.module06.backend.global.exception.CommonErrorCode;
import com.module06.backend.meetingroom.application.command.CreateMeetingRoomCommand;

/*
 * ROOM-03 회의실 등록 요청 본문이며 문자열 시각을 명세의 HH:mm 형식으로 검증한다.
 */
public record CreateMeetingRoomRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 150) String location,
        @NotBlank @Pattern(regexp = "^(?:[01]\\d|2[0-3]):(?:00|30)$") String availableFrom,
        @NotBlank @Pattern(regexp = "^(?:[01]\\d|2[0-3]):(?:00|30)$") String availableTo
) {

    /* API 계약에서 사용하는 분 단위 24시간제 시각 포맷터다. */
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    /* 인증 회사 식별자와 요청 본문을 애플리케이션 명령으로 변환한다. */
    public CreateMeetingRoomCommand toCommand(Long companyId) {
        /* 두 시각을 명세 포맷으로 파싱하고 회사 식별자는 인증 principal 값만 사용한다. */
        return new CreateMeetingRoomCommand(
                companyId,
                name,
                location,
                parseTime(availableFrom),
                parseTime(availableTo)
        );
    }

    /* 시각 문자열을 LocalTime으로 변환하고 잘못된 직접 호출도 공통 입력 오류로 처리한다. */
    private LocalTime parseTime(String value) {
        /* Bean Validation을 거치지 않은 null·형식 오류도 서비스에 전달하지 않는다. */
        try {
            return value == null ? null : LocalTime.parse(value, TIME_FORMATTER);
        } catch (DateTimeParseException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT_VALUE);
        }
    }
}
