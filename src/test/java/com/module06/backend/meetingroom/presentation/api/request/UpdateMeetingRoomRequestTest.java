package com.module06.backend.meetingroom.presentation.api.request;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.module06.backend.meetingroom.application.command.UpdateMeetingRoomCommand;

/*
 * ROOM-04 PATCH JSON에서 필드 미전달과 명시적 null이 구분되는지 검증한다.
 */
@DisplayName("ROOM-04 회의실 수정 요청")
class UpdateMeetingRoomRequestTest {

    /* 실제 HTTP 역직렬화와 같은 Jackson ObjectMapper다. */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /* location 키를 보내지 않고 이름만 보내면 기존 위치 유지 명령이 되는지 검증한다. */
    @Test
    @DisplayName("location 미전달은 기존 위치 유지로 변환한다")
    void distinguishesOmittedLocation() throws Exception {
        /* 이름만 포함한 PATCH JSON을 요청 DTO로 역직렬화한다. */
        UpdateMeetingRoomRequest request = objectMapper.readValue(
                """
                        {
                          "name": "대회의실"
                        }
                        """,
                UpdateMeetingRoomRequest.class
        );

        /* location은 값도 없고 제공 플래그도 false여야 한다. */
        UpdateMeetingRoomCommand command = request.toCommand(10L, "OWNER", 2L);
        assertThat(command.locationProvided()).isFalse();
        assertThat(command.location()).isNull();
        assertThat(command.nameProvided()).isTrue();
        assertThat(command.name()).isEqualTo("대회의실");
    }

    /* location null을 명시하면 위치 삭제 명령이 되는지 검증한다. */
    @Test
    @DisplayName("location 명시적 null은 위치 삭제로 변환한다")
    void distinguishesExplicitNullLocation() throws Exception {
        /* location 키를 null 값으로 명시한 PATCH JSON을 역직렬화한다. */
        UpdateMeetingRoomRequest request = objectMapper.readValue(
                """
                        {
                          "location": null
                        }
                        """,
                UpdateMeetingRoomRequest.class
        );

        /* 값은 null이지만 제공 플래그는 true여서 서비스가 삭제로 해석할 수 있어야 한다. */
        UpdateMeetingRoomCommand command = request.toCommand(10L, "ADMIN", 2L);
        assertThat(command.locationProvided()).isTrue();
        assertThat(command.location()).isNull();
        assertThat(command.hasAnyChange()).isTrue();
    }
}
