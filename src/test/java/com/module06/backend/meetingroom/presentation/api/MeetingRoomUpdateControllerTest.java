package com.module06.backend.meetingroom.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meetingroom.application.command.UpdateMeetingRoomCommand;
import com.module06.backend.meetingroom.application.result.MeetingRoomUpdateResult;
import com.module06.backend.meetingroom.application.usecase.CreateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.DeactivateMeetingRoomUseCase;
import com.module06.backend.meetingroom.application.usecase.UpdateMeetingRoomUseCase;
import com.module06.backend.meetingroom.presentation.api.request.UpdateMeetingRoomRequest;
import com.module06.backend.meetingroom.presentation.api.response.UpdateMeetingRoomResponse;

/*
 * ROOM-04 Controller의 AuthPrincipal·Path·PATCH 본문 전달과 200 응답 변환을 검증한다.
 */
@DisplayName("ROOM-04 회의실 수정 Controller")
class MeetingRoomUpdateControllerTest {

    /* ROOM-04 테스트에서 호출되면 실패하는 ROOM-03 등록 유스케이스 대역이다. */
    private static final CreateMeetingRoomUseCase UNUSED_CREATE_USE_CASE = command -> {
        throw new AssertionError("ROOM-04 수정에서는 등록 유스케이스를 호출하면 안 됩니다.");
    };

    /* ROOM-04 테스트에서 호출되면 실패하는 ROOM-05 비활성화 유스케이스 대역이다. */
    private static final DeactivateMeetingRoomUseCase UNUSED_DEACTIVATE_USE_CASE = command -> {
        throw new AssertionError("ROOM-04 수정에서는 비활성화 유스케이스를 호출하면 안 됩니다.");
    };

    /* 인증 principal과 부분 본문이 수정 명령이 되고 전체 상태 응답으로 변환되는지 검증한다. */
    @Test
    @DisplayName("OWNER가 회의실 일부 정보를 수정하고 전체 상태를 반환한다")
    void returnsUpdatedMeetingRoomResponse() {
        /* 수정 유스케이스에 전달된 명령을 기록할 공간을 준비한다. */
        UpdateMeetingRoomCommand[] capturedCommand = new UpdateMeetingRoomCommand[1];

        /* 명령을 기록하고 수정된 전체 회의실 상태를 반환하는 유스케이스 대역을 만든다. */
        UpdateMeetingRoomUseCase updateUseCase = command -> {
            capturedCommand[0] = command;
            return new MeetingRoomUpdateResult(
                    2L,
                    "회의실 B",
                    "본관 3층",
                    LocalTime.of(9, 0),
                    LocalTime.of(20, 0)
            );
        };
        MeetingRoomCommandController controller = new MeetingRoomCommandController(
                UNUSED_CREATE_USE_CASE,
                updateUseCase,
                UNUSED_DEACTIVATE_USE_CASE
        );

        /* 토큰 principal과 위치·종료 시각만 전달한 PATCH 요청을 준비한다. */
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "OWNER", true, null);
        UpdateMeetingRoomRequest request = new UpdateMeetingRoomRequest();
        request.setLocation("본관 3층");
        request.setAvailableTo("20:00");

        /* 2번 회의실 수정 Controller 메서드를 직접 호출한다. */
        ApiResponse<UpdateMeetingRoomResponse> response = controller.updateMeetingRoom(principal, 2L, request);

        /* 토큰의 회사·역할과 Path·부분 본문이 정확한 애플리케이션 명령으로 결합돼야 한다. */
        assertThat(capturedCommand[0].companyId()).isEqualTo(10L);
        assertThat(capturedCommand[0].requesterRole()).isEqualTo("OWNER");
        assertThat(capturedCommand[0].meetingRoomId()).isEqualTo(2L);
        assertThat(capturedCommand[0].locationProvided()).isTrue();
        assertThat(capturedCommand[0].location()).isEqualTo("본관 3층");
        assertThat(capturedCommand[0].availableFromProvided()).isFalse();
        assertThat(capturedCommand[0].availableTo()).isEqualTo(LocalTime.of(20, 0));

        /* 명세의 200 상태·메시지와 수정된 전체 회의실 정보가 반환돼야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의실 정보를 수정했습니다.");
        assertThat(response.getData().meetingRoomId()).isEqualTo(2L);
        assertThat(response.getData().location()).isEqualTo("본관 3층");
        assertThat(response.getData().availableFrom()).isEqualTo("09:00");
        assertThat(response.getData().availableTo()).isEqualTo("20:00");
    }
}
