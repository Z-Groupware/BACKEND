package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.CancelMeetingCommand;
import com.module06.backend.meeting.application.usecase.CancelMeetingUseCase;

/* MEET-06 Controller의 인증 principal 전달과 data 없는 200 응답 계약을 검증한다. */
@DisplayName("MEET-06 회의 취소 Controller")
class MeetingCancellationControllerTest {

    /* 인증 값과 Path 회의 식별자가 취소 Command로 전달되는지 검증한다. */
    @Test
    @DisplayName("인증 주체와 Path 값으로 회의를 취소하고 data 없는 200을 반환한다")
    void cancelsMeetingWithAuthenticatedPrincipal() {
        /* Controller가 만든 Command를 기록할 한 칸짜리 공간을 준비한다. */
        CancelMeetingCommand[] capturedCommand = new CancelMeetingCommand[1];

        /* 취소 Command만 기록하고 정상 종료하는 유스케이스 대역을 만든다. */
        CancelMeetingUseCase useCase = command -> capturedCommand[0] = command;
        MeetingCancellationController controller = new MeetingCancellationController(useCase);

        /* 회사·구성원·권한이 담긴 host principal과 Path 식별자로 Controller를 호출한다. */
        ApiResponse<Void> response = controller.cancelMeeting(
                new AuthPrincipal(3L, 10L, "MEMBER", false, 100L),
                91L
        );

        /* 조작 가능한 본문 없이 principal과 Path 값만 Command에 들어가야 한다. */
        assertThat(capturedCommand[0]).isEqualTo(new CancelMeetingCommand(
                10L,
                3L,
                "MEMBER",
                false,
                91L
        ));

        /* 최초 취소와 재취소가 공유하는 data 없는 공통 200 응답 계약이어야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("회의를 취소했습니다.");
        assertThat(response.getData()).isNull();
    }
}
