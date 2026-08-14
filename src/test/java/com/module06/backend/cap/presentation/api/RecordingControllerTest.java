package com.module06.backend.cap.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;
import com.module06.backend.cap.application.command.StartRecordingAssemblyCommand;
import com.module06.backend.cap.application.guard.CapMeetingAccessGuard;
import com.module06.backend.cap.application.command.IssueManualRecordingUploadUrlCommand;
import com.module06.backend.cap.application.usecase.DeleteRecordingUseCase;
import com.module06.backend.cap.application.usecase.GetPlaybackUrlUseCase;
import com.module06.backend.cap.application.usecase.IssueManualRecordingUploadUrlUseCase;
import com.module06.backend.cap.application.usecase.RegisterManualRecordingUseCase;
import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;
import com.module06.backend.cap.presentation.api.dto.request.ManualRecordingRequest;
import com.module06.backend.cap.presentation.api.dto.request.ManualRecordingUploadUrlRequest;
import com.module06.backend.cap.presentation.api.dto.request.StartRecordingAssemblyRequest;
import com.module06.backend.cap.presentation.api.dto.response.DeleteRecordingResponse;
import com.module06.backend.cap.presentation.api.dto.response.ManualRecordingResponse;
import com.module06.backend.cap.presentation.api.dto.response.ManualRecordingUploadUrlResponse;
import com.module06.backend.cap.presentation.api.dto.response.PlaybackUrlResponse;
import com.module06.backend.cap.presentation.api.dto.response.RecordingAssemblyResponse;
import com.module06.backend.global.response.ApiResponse;

/*
 * CAP-05 조립 · CAP-10 수동 업로드(및 그 presign) · CAP-14 재생 URL · CAP-15 삭제 Controller가
 * 인증 principal·본문을 유스케이스에 넘기고 공통 응답으로 변환하는지 검증하는 단위 테스트다.
 */
@DisplayName("CAP-05·10·14·15 녹음 Controller")
class RecordingControllerTest {

    /* 조립 트리거가 202 응답에 ASSEMBLING으로 매핑되는지 검증한다. */
    @Test
    @DisplayName("녹음 조립을 202 공통 응답으로 반환한다")
    void returnsAssemblingAccepted() {
        StartRecordingAssemblyCommand[] captured = new StartRecordingAssemblyCommand[1];
        StartRecordingAssemblyUseCase assembleUseCase = command -> {
            captured[0] = command;
            return new StartRecordingAssemblyUseCase.Result("ASSEMBLING", List.of());
        };
        RecordingController controller = new RecordingController(assembleUseCase, failingIssueUploadUrl(),
                failingManual(), failingPlayback(), failingDelete());

        ApiResponse<RecordingAssemblyResponse> response =
                controller.assemble(500L, 7L, new StartRecordingAssemblyRequest(0, 241));

        assertThat(captured[0].meetingId()).isEqualTo(500L);
        assertThat(captured[0].callerId()).isEqualTo(7L);
        assertThat(response.getHttpStatus()).isEqualTo(202);
        assertThat(response.getData()).isNotNull().satisfies(d -> assertThat(d.status()).isEqualTo("ASSEMBLING"));
    }

    /* 수동 업로드용 presigned URL 발급이 200 응답에 s3Key·URL로 매핑되는지 검증한다. */
    @Test
    @DisplayName("수동 녹음 업로드 URL 발급을 200 공통 응답으로 반환한다")
    void returnsManualRecordingUploadUrl() {
        IssueManualRecordingUploadUrlCommand[] captured = new IssueManualRecordingUploadUrlCommand[1];
        IssueManualRecordingUploadUrlUseCase issueUploadUrlUseCase = command -> {
            captured[0] = command;
            return new IssueManualRecordingUploadUrlUseCase.Result(
                    "recordings/org-1/meeting-500/recording.ogg", "https://s3/presigned-put", 900);
        };
        RecordingController controller = new RecordingController(failingAssemble(), issueUploadUrlUseCase,
                failingManual(), failingPlayback(), failingDelete());

        ApiResponse<ManualRecordingUploadUrlResponse> response = controller.manualUploadUrl(
                500L, 7L, new ManualRecordingUploadUrlRequest("recording.ogg", "audio/ogg"));

        assertThat(captured[0].meetingId()).isEqualTo(500L);
        assertThat(captured[0].callerId()).isEqualTo(7L);
        assertThat(captured[0].fileName()).isEqualTo("recording.ogg");
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isNotNull().satisfies(d -> {
            assertThat(d.s3Key()).isEqualTo("recordings/org-1/meeting-500/recording.ogg");
            assertThat(d.uploadUrl()).isEqualTo("https://s3/presigned-put");
            assertThat(d.expiresInSeconds()).isEqualTo(900);
        });
    }

    /* 수동 업로드가 200 응답에 DONE으로 매핑되는지 검증한다. */
    @Test
    @DisplayName("수동 녹음 업로드를 200 공통 응답으로 반환한다")
    void returnsManualRecordingDone() {
        RegisterManualRecordingCommand[] captured = new RegisterManualRecordingCommand[1];
        RegisterManualRecordingUseCase manualUseCase = command -> {
            captured[0] = command;
            return new RegisterManualRecordingUseCase.Result(command.meetingId(), 0L, command.sizeBytes(), "DONE");
        };
        RecordingController controller = new RecordingController(failingAssemble(), failingIssueUploadUrl(),
                manualUseCase, failingPlayback(), failingDelete());

        ApiResponse<ManualRecordingResponse> response = controller.manual(
                500L, 7L, new ManualRecordingRequest("recordings/org-1/meeting-500/recording.ogg", 15_000_000L));

        assertThat(captured[0].meetingId()).isEqualTo(500L);
        assertThat(captured[0].callerId()).isEqualTo(7L);
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isNotNull().satisfies(d -> assertThat(d.status()).isEqualTo("DONE"));
    }

    /* 재생 URL 발급이 200 응답에 url·만료·길이로 매핑되는지 검증한다. */
    @Test
    @DisplayName("재생 URL을 200 공통 응답으로 반환한다")
    void returnsPlaybackUrl() {
        Long[] capturedMeeting = new Long[1];
        CapMeetingAccessGuard.ViewerContext[] capturedRequester = new CapMeetingAccessGuard.ViewerContext[1];
        GetPlaybackUrlUseCase playbackUseCase = (meetingId, requester) -> {
            capturedMeeting[0] = meetingId;
            capturedRequester[0] = requester;
            return new GetPlaybackUrlUseCase.Result("https://s3/playback.ogg", 10800, 3_612_000L);
        };
        RecordingController controller = new RecordingController(failingAssemble(), failingIssueUploadUrl(),
                failingManual(), playbackUseCase, failingDelete());

        ApiResponse<PlaybackUrlResponse> response = controller.playbackUrl(500L, 7L, 1L, null, "MEMBER", false);

        assertThat(capturedMeeting[0]).isEqualTo(500L);
        assertThat(capturedRequester[0].memberId()).isEqualTo(7L);
        assertThat(capturedRequester[0].companyId()).isEqualTo(1L);
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isNotNull().satisfies(d -> {
            assertThat(d.url()).isEqualTo("https://s3/playback.ogg");
            assertThat(d.expiresIn()).isEqualTo(10800);
            assertThat(d.durationMs()).isEqualTo(3_612_000L);
        });
    }

    /* 삭제가 200 응답에 deletedAt·freedBytes로 매핑되고 confirm 쿼리가 전달되는지 검증한다. */
    @Test
    @DisplayName("녹음 삭제를 200 공통 응답으로 반환한다")
    void returnsDeleteResult() {
        Long[] capturedMeeting = new Long[1];
        Long[] capturedCompany = new Long[1];
        boolean[] capturedConfirm = new boolean[1];
        DeleteRecordingUseCase deleteUseCase = (meetingId, companyId, confirm) -> {
            capturedMeeting[0] = meetingId;
            capturedCompany[0] = companyId;
            capturedConfirm[0] = confirm;
            return new DeleteRecordingUseCase.Result(LocalDateTime.of(2026, 8, 10, 9, 0), 10_485_760L);
        };
        RecordingController controller = new RecordingController(failingAssemble(), failingIssueUploadUrl(),
                failingManual(), failingPlayback(), deleteUseCase);

        ApiResponse<DeleteRecordingResponse> response = controller.delete(500L, 1L, true);

        // 회의 ID·회사·confirm이 유스케이스로 정확히 전달돼야 한다.
        assertThat(capturedMeeting[0]).isEqualTo(500L);
        assertThat(capturedCompany[0]).isEqualTo(1L);
        assertThat(capturedConfirm[0]).isTrue();

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("녹음 파일이 삭제되었습니다.");
        assertThat(response.getData()).isNotNull().satisfies(d -> {
            assertThat(d.freedBytes()).isEqualTo(10_485_760L);
            assertThat(d.deletedAt()).isEqualTo(LocalDateTime.of(2026, 8, 10, 9, 0));
        });
    }

    // 해당 메서드 테스트에서 호출되면 안 되는 무동작 대역들.
    private StartRecordingAssemblyUseCase failingAssemble() {
        return command -> {
            throw new AssertionError("이 테스트에서 assemble 유스케이스는 호출되면 안 됩니다.");
        };
    }

    private IssueManualRecordingUploadUrlUseCase failingIssueUploadUrl() {
        return command -> {
            throw new AssertionError("이 테스트에서 issueManualRecordingUploadUrl 유스케이스는 호출되면 안 됩니다.");
        };
    }

    private RegisterManualRecordingUseCase failingManual() {
        return command -> {
            throw new AssertionError("이 테스트에서 manual 유스케이스는 호출되면 안 됩니다.");
        };
    }

    private GetPlaybackUrlUseCase failingPlayback() {
        return (meetingId, requester) -> {
            throw new AssertionError("이 테스트에서 playback 유스케이스는 호출되면 안 됩니다.");
        };
    }

    private DeleteRecordingUseCase failingDelete() {
        return (meetingId, companyId, confirm) -> {
            throw new AssertionError("이 테스트에서 delete 유스케이스는 호출되면 안 됩니다.");
        };
    }
}
