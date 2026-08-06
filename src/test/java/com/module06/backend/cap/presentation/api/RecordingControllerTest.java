package com.module06.backend.cap.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.RegisterManualRecordingCommand;
import com.module06.backend.cap.application.command.StartRecordingAssemblyCommand;
import com.module06.backend.cap.application.usecase.RegisterManualRecordingUseCase;
import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;
import com.module06.backend.cap.presentation.api.dto.request.ManualRecordingRequest;
import com.module06.backend.cap.presentation.api.dto.request.StartRecordingAssemblyRequest;
import com.module06.backend.cap.presentation.api.dto.response.ManualRecordingResponse;
import com.module06.backend.cap.presentation.api.dto.response.RecordingAssemblyResponse;
import com.module06.backend.global.response.ApiResponse;

/*
 * CAP-05 녹음 종료/조립 · CAP-10 수동 업로드 Controller가 인증 principal·본문을 Command로 합쳐 유스케이스에 넘기고
 * 공통 응답으로 변환하는지 검증하는 단위 테스트다.
 */
@DisplayName("CAP-05·CAP-10 녹음 Controller")
class RecordingControllerTest {

    /* 조립 트리거가 202 응답에 ASSEMBLING 상태로 매핑되는지 검증한다. */
    @Test
    @DisplayName("녹음 조립을 202 공통 응답으로 반환한다")
    void returnsAssemblingAccepted() {
        StartRecordingAssemblyCommand[] captured = new StartRecordingAssemblyCommand[1];
        StartRecordingAssemblyUseCase assembleUseCase = command -> {
            captured[0] = command;
            return new StartRecordingAssemblyUseCase.Result("ASSEMBLING", List.of());
        };
        RecordingController controller = new RecordingController(assembleUseCase, failingManual());

        ApiResponse<RecordingAssemblyResponse> response =
                controller.assemble(500L, 7L, new StartRecordingAssemblyRequest(0, 241));

        assertThat(captured[0].meetingId()).isEqualTo(500L);
        assertThat(captured[0].callerId()).isEqualTo(7L);
        assertThat(captured[0].lastSegmentSeq()).isZero();
        assertThat(captured[0].lastSeq()).isEqualTo(241);

        assertThat(response.getHttpStatus()).isEqualTo(202);
        assertThat(response.getMessage()).isEqualTo("녹음 조립을 시작합니다.");
        assertThat(response.getData()).isNotNull().satisfies(data -> {
            assertThat(data.status()).isEqualTo("ASSEMBLING");
            assertThat(data.missingSeqs()).isEmpty();
        });
    }

    /* 수동 업로드가 200 응답에 DONE 상태로 매핑되는지 검증한다. */
    @Test
    @DisplayName("수동 녹음 업로드를 200 공통 응답으로 반환한다")
    void returnsManualRecordingDone() {
        RegisterManualRecordingCommand[] captured = new RegisterManualRecordingCommand[1];
        RegisterManualRecordingUseCase manualUseCase = command -> {
            captured[0] = command;
            return new RegisterManualRecordingUseCase.Result(command.meetingId(), 0L, command.sizeBytes(), "DONE");
        };
        RecordingController controller = new RecordingController(failingAssemble(), manualUseCase);

        ApiResponse<ManualRecordingResponse> response = controller.manual(
                500L, 7L,
                new ManualRecordingRequest("recordings/org-1/meeting-500/recording.ogg", 15_000_000L));

        // 회의 ID·조회자·본문이 Command로 정확히 합쳐져야 한다(callerId는 principal).
        assertThat(captured[0].meetingId()).isEqualTo(500L);
        assertThat(captured[0].callerId()).isEqualTo(7L);
        assertThat(captured[0].s3Key()).isEqualTo("recordings/org-1/meeting-500/recording.ogg");
        assertThat(captured[0].sizeBytes()).isEqualTo(15_000_000L);

        // 공통 200 상태와 등록 성공 메시지, DONE 데이터가 응답에 담겨야 한다.
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("녹음 파일이 등록되었습니다.");
        assertThat(response.getData()).isNotNull().satisfies(data -> {
            assertThat(data.meetingId()).isEqualTo(500L);
            assertThat(data.durationMs()).isZero();
            assertThat(data.sizeBytes()).isEqualTo(15_000_000L);
            assertThat(data.status()).isEqualTo("DONE");
        });
    }

    // 해당 메서드 테스트에서 호출되면 안 되는 무동작 대역들.
    private StartRecordingAssemblyUseCase failingAssemble() {
        return command -> {
            throw new AssertionError("manual 테스트에서 assemble 유스케이스는 호출되면 안 됩니다.");
        };
    }

    private RegisterManualRecordingUseCase failingManual() {
        return command -> {
            throw new AssertionError("assemble 테스트에서 manual 유스케이스는 호출되면 안 됩니다.");
        };
    }
}
