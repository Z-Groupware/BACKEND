package com.module06.backend.cap.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.module06.backend.cap.application.usecase.GetActiveCaptureUseCase;
import com.module06.backend.cap.presentation.api.dto.response.ActiveCaptureResponse;
import com.module06.backend.global.response.ApiResponse;

/*
 * CAP-09 Controller가 인증 principal의 memberId로 유스케이스를 호출하고, 결과 유무에 따라
 * 200 공통 응답(data 또는 null)으로 변환하는지 검증하는 단위 테스트다.
 */
@DisplayName("CAP-09 진행 중 캡처 조회 Controller")
class CaptureQueryControllerTest {

    /* 진행 중 캡처가 있으면 200 응답에 결과 필드가 매핑되는지 검증한다. */
    @Test
    @DisplayName("진행 중 캡처를 200 공통 응답으로 반환한다")
    void returnsActiveCapture() {
        /* 유스케이스에 전달된 memberId를 기록할 공간을 준비한다. */
        Long[] capturedMemberId = new Long[1];

        /* memberId를 기록하고 진행 중 캡처 한 건을 반환하는 유스케이스 대역을 만든다. */
        GetActiveCaptureUseCase useCase = memberId -> {
            capturedMemberId[0] = memberId;
            return Optional.of(new GetActiveCaptureUseCase.Result(500L, null, 0, 187, 7L, false, 2_810_000L));
        };
        CaptureQueryController controller = new CaptureQueryController(useCase);

        /* 인증 principal의 memberId 7번으로 조회를 호출한다. */
        ApiResponse<ActiveCaptureResponse> response = controller.active(7L);

        /* 조회 기준 memberId는 principal 값으로 유스케이스에 전달돼야 한다. */
        assertThat(capturedMemberId[0]).isEqualTo(7L);

        /* 공통 200 상태와 조회 성공 메시지, 결과 필드가 응답에 담겨야 한다. */
        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getMessage()).isEqualTo("조회 성공");
        assertThat(response.getData()).isNotNull().satisfies(data -> {
            assertThat(data.meetingId()).isEqualTo(500L);
            assertThat(data.lastSeq()).isEqualTo(187);
            assertThat(data.recorderPersonId()).isEqualTo(7L);
            assertThat(data.canTakeover()).isFalse();
            assertThat(data.elapsedMs()).isEqualTo(2_810_000L);
        });
    }

    /* 진행 중 캡처가 없으면 200 응답이면서 data가 null인지 검증한다. */
    @Test
    @DisplayName("진행 중 캡처가 없으면 200 응답에 data는 null이다")
    void returnsNullDataWhenNoActiveCapture() {
        GetActiveCaptureUseCase useCase = memberId -> Optional.empty();
        CaptureQueryController controller = new CaptureQueryController(useCase);

        ApiResponse<ActiveCaptureResponse> response = controller.active(7L);

        assertThat(response.getHttpStatus()).isEqualTo(200);
        assertThat(response.getData()).isNull();
    }
}
