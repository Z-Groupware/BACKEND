package com.module06.backend.cap.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.module06.backend.cap.application.command.SubmitCaptionsCommand;
import com.module06.backend.cap.application.usecase.GetCaptionsUseCase;
import com.module06.backend.cap.application.usecase.SubmitCaptionsUseCase;
import com.module06.backend.cap.application.usecase.SubscribeToCaptionsUseCase;
import com.module06.backend.cap.presentation.api.dto.request.SubmitCaptionsRequest;
import com.module06.backend.cap.presentation.api.dto.response.CaptionsResponse;
import com.module06.backend.global.response.ApiResponse;

/*
 * CAP-11 자막 전송 · CAP-12 자막 전체 조회 · CAP-13 자막 실시간 구독(SSE) Controller가 인증 principal·
 * 본문을 유스케이스로 그대로 넘기고 공통 응답(혹은 SseEmitter)으로 변환하는지 검증하는 단위 테스트다.
 */
@DisplayName("CAP-11·12·13 자막 Controller")
class CaptionControllerTest {

    /* 본문의 chunks가 그대로 Command로 변환되고, memberId는 인증 principal에서 온 값이 쓰이는지 검증한다. */
    @Test
    @DisplayName("자막 배치 전송을 202 공통 응답으로 반환한다")
    void submitsCaptionsAccepted() {
        SubmitCaptionsCommand[] captured = new SubmitCaptionsCommand[1];
        SubmitCaptionsUseCase useCase = command -> captured[0] = command;
        CaptionController controller = new CaptionController(useCase, failingGetCaptions(), failingSubscribe());

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

    /* 인증 principal 4개(memberId·companyId·role·isAdmin)가 그대로 Requester로 조립되고, 200 공통 응답으로
       매핑되는지 검증한다. */
    @Test
    @DisplayName("자막 전체 조회를 200 공통 응답으로 반환한다")
    void listsCaptionsOk() {
        GetCaptionsUseCase.Requester[] captured = new GetCaptionsUseCase.Requester[1];
        GetCaptionsUseCase getCaptionsUseCase = (meetingId, requester) -> {
            captured[0] = requester;
            return new GetCaptionsUseCase.Result(List.of(
                    new GetCaptionsUseCase.CaptionItem(12, 7L, 184_000, 186_200,
                            "다음 스프린트 목표부터", new BigDecimal("-18.4"))));
        };
        CaptionController controller = new CaptionController(failingSubmit(), getCaptionsUseCase, failingSubscribe());

        ApiResponse<CaptionsResponse> response = controller.list(500L, 7L, 1L, "MEMBER", false);

        assertThat(captured[0].memberId()).isEqualTo(7L);
        assertThat(captured[0].companyId()).isEqualTo(1L);
        assertThat(captured[0].role()).isEqualTo("MEMBER");
        assertThat(captured[0].isAdmin()).isFalse();
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData().captions()).hasSize(1);
        assertThat(response.getData().captions().get(0).text()).isEqualTo("다음 스프린트 목표부터");
    }

    /* meetingId·memberId가 그대로 유스케이스에 전달되고, 유스케이스가 만든 SseEmitter가 그대로 반환되는지 검증한다. */
    @Test
    @DisplayName("자막 실시간 구독은 유스케이스가 만든 SseEmitter를 그대로 반환한다")
    void streamsReturnsEmitterFromUseCase() {
        Long[] capturedMeeting = new Long[1];
        Long[] capturedMember = new Long[1];
        SseEmitter expected = new SseEmitter(0L);
        SubscribeToCaptionsUseCase subscribeUseCase = (meetingId, memberId) -> {
            capturedMeeting[0] = meetingId;
            capturedMember[0] = memberId;
            return expected;
        };
        CaptionController controller = new CaptionController(failingSubmit(), failingGetCaptions(), subscribeUseCase);

        SseEmitter result = controller.stream(500L, 7L);

        assertThat(capturedMeeting[0]).isEqualTo(500L);
        assertThat(capturedMember[0]).isEqualTo(7L);
        assertThat(result).isSameAs(expected);
    }

    private SubmitCaptionsUseCase failingSubmit() {
        return command -> {
            throw new AssertionError("이 테스트에서 submit 유스케이스는 호출되면 안 됩니다.");
        };
    }

    private GetCaptionsUseCase failingGetCaptions() {
        return (meetingId, requester) -> {
            throw new AssertionError("이 테스트에서 자막 조회 유스케이스는 호출되면 안 됩니다.");
        };
    }

    private SubscribeToCaptionsUseCase failingSubscribe() {
        return (meetingId, memberId) -> {
            throw new AssertionError("이 테스트에서 구독 유스케이스는 호출되면 안 됩니다.");
        };
    }
}
