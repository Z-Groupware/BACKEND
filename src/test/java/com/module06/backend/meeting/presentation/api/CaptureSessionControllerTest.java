package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.StartCaptureSessionCommand;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult.RosterEntry;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult.RosterType;
import com.module06.backend.meeting.application.usecase.StartCaptureSessionUseCase;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.presentation.api.response.CaptureSessionStartResponse;

/*
 * CAP-01 Controller의 AuthPrincipal·Path 전달과 201 응답 변환을 검증한다.
 */
@DisplayName("CAP-01 캡처 세션 시작 Controller")
class CaptureSessionControllerTest {

    /* 인증 principal과 Path가 시작 명령이 되고 명세 응답으로 변환되는지 검증한다. */
    @Test
    @DisplayName("host가 캡처 세션을 시작하고 201 응답을 받는다")
    void startsCaptureSessionAndReturnsCreatedResponse() {
        /* 유스케이스에 전달된 명령을 기록할 공간을 준비한다. */
        StartCaptureSessionCommand[] capturedCommand = new StartCaptureSessionCommand[1];

        /* 명령을 기록하고 CAP-01 명세 예시 결과를 반환하는 유스케이스 대역을 만든다. */
        StartCaptureSessionUseCase useCase = command -> {
            capturedCommand[0] = command;
            return new CaptureSessionStartResult(
                    15L,
                    CaptureSessionStatus.ACTIVE,
                    false,
                    3L,
                    1_785_891_600_000L,
                    List.of(
                            new RosterEntry("member:12", 12L, "김서준", RosterType.MEMBER),
                            new RosterEntry("unknown_person", null, "명단 외", RosterType.UNKNOWN)
                    )
            );
        };
        CaptureSessionController controller = new CaptureSessionController(useCase);

        /* 회사 10의 host 3번 principal로 91번 회의 캡처 세션을 시작한다. */
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "MEMBER", false, 100L);
        ApiResponse<CaptureSessionStartResponse> response = controller.startCaptureSession(principal, 91L);

        /* 토큰 회사·구성원과 Path 회의 ID가 정확한 시작 명령으로 결합돼야 한다. */
        assertThat(capturedCommand[0]).isEqualTo(new StartCaptureSessionCommand(10L, 3L, 91L));

        /* CAP-01의 201 공통 성공 응답과 D 소유 필드를 그대로 반환해야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(201);
        assertThat(response.getMessage()).isEqualTo("캡처 세션을 시작했습니다.");
        assertThat(response.getData().captureSessionId()).isEqualTo(15L);
        assertThat(response.getData().status()).isEqualTo("ACTIVE");
        assertThat(response.getData().isPaused()).isFalse();
        assertThat(response.getData().startedBy()).isEqualTo(3L);
        assertThat(response.getData().startedAtEpochMs()).isEqualTo(1_785_891_600_000L);

        /* roster는 실제 구성원과 명단 외 sentinel을 명세 문자열 타입으로 반환해야 한다. */
        assertThat(response.getData().roster())
                .extracting(CaptureSessionStartResponse.RosterEntryResponse::type)
                .containsExactly("MEMBER", "UNKNOWN");
        assertThat(response.getData().roster().get(1).personKey()).isEqualTo("unknown_person");
        assertThat(response.getData().roster().get(1).memberId()).isNull();
    }
}
