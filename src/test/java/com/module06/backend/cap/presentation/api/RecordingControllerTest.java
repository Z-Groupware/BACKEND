package com.module06.backend.cap.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.StartRecordingAssemblyCommand;
import com.module06.backend.cap.application.usecase.StartRecordingAssemblyUseCase;
import com.module06.backend.cap.presentation.api.dto.request.StartRecordingAssemblyRequest;
import com.module06.backend.cap.presentation.api.dto.response.RecordingAssemblyResponse;
import com.module06.backend.global.response.ApiResponse;

/*
 * CAP-05 녹음 종료/조립 Controller가 인증 principal·본문을 Command로 합쳐 유스케이스에 넘기고
 * 202 공통 응답으로 변환하는지 검증하는 단위 테스트다.
 */
@DisplayName("CAP-05 녹음 종료/조립 Controller")
class RecordingControllerTest {

    /* 조립 트리거가 202 응답에 ASSEMBLING 상태로 매핑되는지 검증한다. */
    @Test
    @DisplayName("녹음 조립을 202 공통 응답으로 반환한다")
    void returnsAssemblingAccepted() {
        /* 유스케이스에 전달된 Command를 기록할 공간을 준비한다. */
        StartRecordingAssemblyCommand[] captured = new StartRecordingAssemblyCommand[1];

        /* Command를 기록하고 ASSEMBLING 결과를 반환하는 유스케이스 대역을 만든다. */
        StartRecordingAssemblyUseCase useCase = command -> {
            captured[0] = command;
            return new StartRecordingAssemblyUseCase.Result("ASSEMBLING", List.of());
        };
        RecordingController controller = new RecordingController(useCase);

        /* 회의 500, principal 7, 본문 lastSegmentSeq=0·lastSeq=241로 종료를 호출한다. */
        ApiResponse<RecordingAssemblyResponse> response =
                controller.assemble(500L, 7L, new StartRecordingAssemblyRequest(0, 241));

        /* 회의 ID·조회자·본문 값이 Command로 정확히 합쳐져야 한다(callerId는 principal). */
        assertThat(captured[0].meetingId()).isEqualTo(500L);
        assertThat(captured[0].callerId()).isEqualTo(7L);
        assertThat(captured[0].lastSegmentSeq()).isZero();
        assertThat(captured[0].lastSeq()).isEqualTo(241);

        /* 공통 202 상태와 조립 시작 메시지, ASSEMBLING 데이터가 응답에 담겨야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(202);
        assertThat(response.getMessage()).isEqualTo("녹음 조립을 시작합니다.");
        assertThat(response.getData()).isNotNull().satisfies(data -> {
            assertThat(data.status()).isEqualTo("ASSEMBLING");
            assertThat(data.missingSeqs()).isEmpty();
        });
    }
}
