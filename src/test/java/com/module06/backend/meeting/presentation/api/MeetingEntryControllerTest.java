package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.EnterMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingEntryResult;
import com.module06.backend.meeting.application.usecase.CreateMeetingUseCase;
import com.module06.backend.meeting.application.usecase.EnterMeetingUseCase;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.presentation.api.response.MeetingEntryResponse;

/*
 * MEET-07 Controller의 AuthPrincipal·Path 전달과 200 응답 변환을 검증한다.
 */
@DisplayName("MEET-07 회의 입장 Controller")
class MeetingEntryControllerTest {

    /* MEET-07 테스트에서 호출되면 실패하는 MEET-01 예약 유스케이스 대역이다. */
    private static final CreateMeetingUseCase UNUSED_CREATE_USE_CASE = command -> {
        throw new AssertionError("MEET-07 입장에서는 예약 유스케이스를 호출하면 안 됩니다.");
    };

    /* 인증 principal과 Path가 입장 명령이 되고 화면 분기 응답으로 변환되는지 검증한다. */
    @Test
    @DisplayName("예약 참석자가 입장하고 진행 상태를 반환한다")
    void entersMeetingAndReturnsProgressState() {
        /* MEET-07 유스케이스에 전달된 명령을 기록할 공간을 준비한다. */
        EnterMeetingCommand[] capturedCommand = new EnterMeetingCommand[1];

        /* 명령을 기록하고 명세 예시 입장 결과를 반환하는 유스케이스 대역을 만든다. */
        EnterMeetingUseCase enterUseCase = command -> {
            capturedCommand[0] = command;
            return new MeetingEntryResult(
                    91L,
                    MeetingStatus.IN_PROGRESS,
                    LocalDateTime.of(2026, 8, 6, 13, 58, 12),
                    4,
                    true,
                    true,
                    true
            );
        };
        MeetingController controller = new MeetingController(UNUSED_CREATE_USE_CASE, enterUseCase);

        /* 회사 10의 구성원 3번 principal로 91번 회의 입장 메서드를 호출한다. */
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "MEMBER", false, 100L);
        ApiResponse<MeetingEntryResponse> response = controller.enterMeeting(principal, 91L);

        /* 토큰 회사·구성원과 Path 회의 식별자가 정확한 MEET-07 명령으로 결합돼야 한다. */
        assertThat(capturedCommand[0]).isEqualTo(new EnterMeetingCommand(10L, 3L, 91L));

        /* 명세의 상태·시작 일시·화면 제어 값과 200 성공 응답을 반환해야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의에 입장했습니다.");
        assertThat(response.getData().meetingId()).isEqualTo(91L);
        assertThat(response.getData().status()).isEqualTo("IN_PROGRESS");
        assertThat(response.getData().startedAt()).isEqualTo("2026-08-06T13:58:12");
        assertThat(response.getData().attendeeCount()).isEqualTo(4);
        assertThat(response.getData().isHost()).isTrue();
        assertThat(response.getData().canControlRecording()).isTrue();
    }
}
