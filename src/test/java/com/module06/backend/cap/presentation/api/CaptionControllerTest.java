package com.module06.backend.cap.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.command.SubmitCaptionsCommand;
import com.module06.backend.cap.application.usecase.SubmitCaptionsUseCase;
import com.module06.backend.cap.presentation.api.dto.request.SubmitCaptionsRequest;
import com.module06.backend.global.response.ApiResponse;

/*
 * CAP-11 자막 전송 Controller가 인증 principal(memberId)·본문을 유스케이스로 그대로 넘기고
 * 202 공통 응답으로 변환하는지 검증하는 단위 테스트다.
 */
@DisplayName("CAP-11 자막 Controller")
class CaptionControllerTest {

    /* 본문의 chunks가 그대로 Command로 변환되고, memberId는 인증 principal에서 온 값이 쓰이는지 검증한다. */
    @Test
    @DisplayName("자막 배치 전송을 202 공통 응답으로 반환한다")
    void submitsCaptionsAccepted() {
        SubmitCaptionsCommand[] captured = new SubmitCaptionsCommand[1];
        SubmitCaptionsUseCase useCase = command -> captured[0] = command;
        CaptionController controller = new CaptionController(useCase);

        SubmitCaptionsRequest request = new SubmitCaptionsRequest(List.of(
                new SubmitCaptionsRequest.ChunkRequest(41, 623_400, 625_100, "이거 제가 할게요", new BigDecimal("-12.4"))));

        ApiResponse<Void> response = controller.submit(500L, 7L, request);

        assertThat(captured[0].meetingId()).isEqualTo(500L);
        assertThat(captured[0].memberId()).isEqualTo(7L);
        assertThat(captured[0].chunks()).hasSize(1);
        assertThat(captured[0].chunks().get(0).seq()).isEqualTo(41);
        assertThat(captured[0].chunks().get(0).rms()).isEqualByComparingTo("-12.4");
        assertThat(response.getHttpStatus()).isEqualTo(202);
        assertThat(response.getMessage()).isEqualTo("자막이 저장·전달되었습니다.");
    }
}
