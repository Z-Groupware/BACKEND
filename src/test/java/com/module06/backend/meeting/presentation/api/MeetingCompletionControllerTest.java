package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.CompleteMeetingCommand;
import com.module06.backend.meeting.application.result.MeetingCompletionResult;
import com.module06.backend.meeting.application.usecase.CompleteMeetingUseCase;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.domain.model.MeetingStatus;
import com.module06.backend.meeting.presentation.api.response.MeetingCompletionResponse;

/* MEET-08 Controller의 인증 명령 조립과 성공 응답 계약을 검증한다. */
@DisplayName("MEET-08 회의 종료 Controller")
class MeetingCompletionControllerTest {

    /* 인증 principal이 명령으로 전달되고 종료 결과가 200 응답으로 변환되는지 확인한다. */
    @Test
    @DisplayName("회의 종료 결과를 200 공통 응답으로 반환한다")
    void returnsCompletedMeetingResponse() {
        /* 유스케이스에 전달된 종료 명령을 기록할 공간을 준비한다. */
        CompleteMeetingCommand[] capturedCommand = new CompleteMeetingCommand[1];

        /* 명령을 기록하고 명세 예시 종료 결과를 반환하는 유스케이스 대역을 만든다. */
        CompleteMeetingUseCase useCase = command -> {
            capturedCommand[0] = command;
            return new MeetingCompletionResult(
                    91L,
                    MeetingStatus.DONE,
                    "PENDING",
                    CaptureSessionStatus.ENDED,
                    LocalDateTime.of(2026, 8, 6, 13, 58, 12),
                    LocalDateTime.of(2026, 8, 6, 15, 2, 40),
                    64L
            );
        };
        MeetingCompletionController controller = new MeetingCompletionController(useCase);

        /* 회사 10의 ADMIN 8번 principal로 91번 회의를 종료한다. */
        AuthPrincipal principal = new AuthPrincipal(8L, 10L, "ADMIN", true, 100L);
        ApiResponse<MeetingCompletionResponse> response = controller.completeMeeting(principal, 91L);

        /* 인증 회사·구성원·권한과 Path 식별자가 본문 입력 없이 명령에 전달돼야 한다. */
        assertThat(capturedCommand[0]).isEqualTo(new CompleteMeetingCommand(
                10L,
                8L,
                "ADMIN",
                true,
                91L
        ));

        /* 명세의 200 상태와 성공 메시지 및 완료 상태를 반환해야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의를 종료했습니다.");
        assertThat(response.getData().meetingId()).isEqualTo(91L);
        assertThat(response.getData().meetingStatus()).isEqualTo("DONE");
        assertThat(response.getData().processingStatus()).isEqualTo("PENDING");
        assertThat(response.getData().captureSessionStatus()).isEqualTo("ENDED");

        /* 실제 시각은 초 단위 문자열이며 예약 시간이 아닌 실측 64분이어야 한다. */
        assertThat(response.getData().startedAt()).isEqualTo("2026-08-06T13:58:12");
        assertThat(response.getData().endedAt()).isEqualTo("2026-08-06T15:02:40");
        assertThat(response.getData().durationMinutes()).isEqualTo(64L);
    }
}
