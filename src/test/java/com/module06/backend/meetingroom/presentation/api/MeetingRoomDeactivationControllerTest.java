package com.module06.backend.meetingroom.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meetingroom.application.command.DeactivateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.usecase.CreateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.DeactivateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.UpdateMeetingRoomUseCase;

/*
 * ROOM-05 Controller의 AuthPrincipal·Path 전달과 200 빈 data 응답을 검증한다.
 */
@DisplayName("ROOM-05 회의실 비활성화 Controller")
class MeetingRoomDeactivationControllerTest {

    /* ROOM-05 테스트에서 호출되면 실패하는 ROOM-03 등록 유스케이스 대역이다. */
    private static final CreateMeetingRoomUseCase UNUSED_CREATE_USE_CASE = command -> {
        throw new AssertionError("ROOM-05 비활성화에서는 등록 유스케이스를 호출하면 안 됩니다.");
    };

    /* ROOM-05 테스트에서 호출되면 실패하는 ROOM-04 수정 유스케이스 대역이다. */
    private static final UpdateMeetingRoomUseCase UNUSED_UPDATE_USE_CASE = command -> {
        throw new AssertionError("ROOM-05 비활성화에서는 수정 유스케이스를 호출하면 안 됩니다.");
    };

    /* 인증 principal과 Path가 비활성화 명령이 되고 빈 data 성공 응답으로 변환되는지 검증한다. */
    @Test
    @DisplayName("ADMIN이 회의실을 비활성화하고 200 응답을 반환한다")
    void deactivatesMeetingRoomAndReturnsSuccessResponse() {
        /* 비활성화 유스케이스에 전달된 명령을 기록할 공간을 준비한다. */
        DeactivateMeetingRoomCommand[] capturedCommand = new DeactivateMeetingRoomCommand[1];

        /* 전달된 명령만 기록하고 정상 완료하는 ROOM-05 유스케이스 대역을 만든다. */
        DeactivateMeetingRoomUseCase deactivateUseCase = command -> capturedCommand[0] = command;
        MeetingRoomCommandController controller = new MeetingRoomCommandController(
                UNUSED_CREATE_USE_CASE,
                UNUSED_UPDATE_USE_CASE,
                deactivateUseCase
        );

        /* 회사 10의 ADMIN principal로 2번 회의실 비활성화 메서드를 호출한다. */
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "ADMIN", true, null);
        ApiResponse<Void> response = controller.deactivateMeetingRoom(principal, 2L);

        /* 토큰의 회사·역할과 Path 식별자가 정확한 애플리케이션 명령으로 결합돼야 한다. */
        assertThat(capturedCommand[0]).isEqualTo(new DeactivateMeetingRoomCommand(10L, "ADMIN", 2L));

        /* 명세의 200 상태·성공 메시지와 null data가 반환돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의실을 비활성화했습니다.");
        assertThat(response.getData()).isNull();
    }
}
