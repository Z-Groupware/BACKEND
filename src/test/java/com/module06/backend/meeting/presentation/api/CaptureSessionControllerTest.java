package com.module06.backend.meeting.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.global.response.ApiResponse;
import com.module06.backend.global.security.AuthPrincipal;
import com.module06.backend.meeting.application.command.PauseCaptureSessionCommand;
import com.module06.backend.meeting.application.command.ResumeCaptureSessionCommand;
import com.module06.backend.meeting.application.command.StartCaptureSessionCommand;
import com.module06.backend.meeting.application.result.CaptureSessionPauseResult;
import com.module06.backend.meeting.application.result.CaptureSessionResumeResult;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult.RosterEntry;
import com.module06.backend.meeting.application.result.CaptureSessionStartResult.RosterType;
import com.module06.backend.meeting.application.usecase.PauseCaptureSessionUseCase;
import com.module06.backend.meeting.application.usecase.ResumeCaptureSessionUseCase;
import com.module06.backend.meeting.application.usecase.StartCaptureSessionUseCase;
import com.module06.backend.meeting.domain.model.CaptureSessionStatus;
import com.module06.backend.meeting.presentation.api.response.CaptureSessionPauseResponse;
import com.module06.backend.meeting.presentation.api.response.CaptureSessionResumeResponse;
import com.module06.backend.meeting.presentation.api.response.CaptureSessionStartResponse;

/*
 * CAP-01~03 Controller의 AuthPrincipal·Path 전달과 명세 응답 변환을 검증한다.
 */
@DisplayName("CAP-01~03 캡처 세션 Controller")
class CaptureSessionControllerTest {

    /* CAP-01 테스트에서 호출되면 실패하는 CAP-02 일시정지 유스케이스 대역이다. */
    private static final PauseCaptureSessionUseCase UNUSED_PAUSE_USE_CASE = command -> {
        /* 시작 API가 일시정지 유스케이스로 잘못 연결되면 테스트를 즉시 실패시킨다. */
        throw new AssertionError("CAP-01 시작에서는 일시정지 유스케이스를 호출하면 안 됩니다.");
    };

    /* CAP-02 테스트에서 호출되면 실패하는 CAP-01 시작 유스케이스 대역이다. */
    private static final StartCaptureSessionUseCase UNUSED_START_USE_CASE = command -> {
        /* 일시정지·재개 API가 시작 유스케이스로 잘못 연결되면 테스트를 즉시 실패시킨다. */
        throw new AssertionError("CAP-02·03에서는 시작 유스케이스를 호출하면 안 됩니다.");
    };

    /* CAP-01·02 테스트에서 호출되면 실패하는 CAP-03 재개 유스케이스 대역이다. */
    private static final ResumeCaptureSessionUseCase UNUSED_RESUME_USE_CASE = command -> {
        /* 시작·일시정지 API가 재개 유스케이스로 잘못 연결되면 테스트를 즉시 실패시킨다. */
        throw new AssertionError("CAP-01·02에서는 재개 유스케이스를 호출하면 안 됩니다.");
    };

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
        CaptureSessionController controller = new CaptureSessionController(
                useCase,
                UNUSED_PAUSE_USE_CASE,
                UNUSED_RESUME_USE_CASE
        );

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

    /* 인증 principal과 Path가 일시정지 명령이 되고 200 응답으로 변환되는지 검증한다. */
    @Test
    @DisplayName("host가 캡처를 일시정지하고 200 응답을 받는다")
    void pausesCaptureSessionAndReturnsOkResponse() {
        /* 일시정지 유스케이스에 전달된 명령을 기록할 공간을 준비한다. */
        PauseCaptureSessionCommand[] capturedCommand = new PauseCaptureSessionCommand[1];

        /* 명령을 기록하고 CAP-02 명세 예시 결과를 반환하는 유스케이스 대역을 만든다. */
        PauseCaptureSessionUseCase pauseUseCase = command -> {
            /* 토큰과 Path가 결합된 명령을 이후 검증을 위해 기록한다. */
            capturedCommand[0] = command;
            return new CaptureSessionPauseResult(
                    15L,
                    CaptureSessionStatus.PAUSED,
                    true,
                    LocalDateTime.of(2026, 8, 6, 14, 31, 8)
            );
        };
        CaptureSessionController controller = new CaptureSessionController(
                UNUSED_START_USE_CASE,
                pauseUseCase,
                UNUSED_RESUME_USE_CASE
        );

        /* 회사 10의 host 3번 principal로 91번 회의 캡처 일시정지를 호출한다. */
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "MEMBER", false, 100L);
        ApiResponse<CaptureSessionPauseResponse> response = controller.pauseCaptureSession(principal, 91L);

        /* 토큰 회사·구성원과 Path 회의 ID가 정확한 CAP-02 명령으로 결합돼야 한다. */
        assertThat(capturedCommand[0]).isEqualTo(new PauseCaptureSessionCommand(10L, 3L, 91L));

        /* 명세의 200 메시지와 PAUSED 상태·초 단위 KST 시각을 반환해야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("캡처를 일시정지했습니다.");
        assertThat(response.getData().captureSessionId()).isEqualTo(15L);
        assertThat(response.getData().status()).isEqualTo("PAUSED");
        assertThat(response.getData().isPaused()).isTrue();
        assertThat(response.getData().pausedAt()).isEqualTo("2026-08-06T14:31:08");
    }

    /* 인증 principal과 Path가 재개 명령이 되고 200 응답으로 변환되는지 검증한다. */
    @Test
    @DisplayName("host가 캡처를 재개하고 200 응답을 받는다")
    void resumesCaptureSessionAndReturnsOkResponse() {
        /* 재개 유스케이스에 전달된 명령을 기록할 공간을 준비한다. */
        ResumeCaptureSessionCommand[] capturedCommand = new ResumeCaptureSessionCommand[1];

        /* 명령을 기록하고 CAP-03 명세 예시 결과를 반환하는 유스케이스 대역을 만든다. */
        ResumeCaptureSessionUseCase resumeUseCase = command -> {
            /* 토큰과 Path가 결합된 명령을 이후 검증을 위해 기록한다. */
            capturedCommand[0] = command;
            return new CaptureSessionResumeResult(
                    15L,
                    CaptureSessionStatus.ACTIVE,
                    false,
                    LocalDateTime.of(2026, 8, 6, 14, 36, 22)
            );
        };
        CaptureSessionController controller = new CaptureSessionController(
                UNUSED_START_USE_CASE,
                UNUSED_PAUSE_USE_CASE,
                resumeUseCase
        );

        /* 회사 10의 host 3번 principal로 91번 회의 캡처 재개를 호출한다. */
        AuthPrincipal principal = new AuthPrincipal(3L, 10L, "MEMBER", false, 100L);
        ApiResponse<CaptureSessionResumeResponse> response = controller.resumeCaptureSession(principal, 91L);

        /* 토큰 회사·구성원과 Path 회의 ID가 정확한 CAP-03 명령으로 결합돼야 한다. */
        assertThat(capturedCommand[0]).isEqualTo(new ResumeCaptureSessionCommand(10L, 3L, 91L));

        /* 명세의 200 메시지와 ACTIVE 상태·초 단위 KST 시각을 반환해야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("캡처를 재개했습니다.");
        assertThat(response.getData().captureSessionId()).isEqualTo(15L);
        assertThat(response.getData().status()).isEqualTo("ACTIVE");
        assertThat(response.getData().isPaused()).isFalse();
        assertThat(response.getData().resumedAt()).isEqualTo("2026-08-06T14:36:22");
    }
}
