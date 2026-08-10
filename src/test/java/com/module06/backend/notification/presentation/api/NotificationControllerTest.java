package com.module06.backend.notification.presentation.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import com.module06.backend.notification.application.port.out.NotificationStreamPort;

/* memberId가 그대로 포트로 전달되고, 포트가 만든 SseEmitter가 그대로 반환되는지 검증하는 단위 테스트다. */
@DisplayName("알림 실시간 구독 Controller")
class NotificationControllerTest {

    @Test
    @DisplayName("구독은 포트가 만든 SseEmitter를 그대로 반환하고, memberId를 그대로 넘긴다")
    void streamReturnsEmitterFromPort() {
        Long[] capturedMemberId = new Long[1];
        SseEmitter expected = new SseEmitter(0L);
        NotificationStreamPort port = memberId -> {
            capturedMemberId[0] = memberId;
            return expected;
        };
        NotificationController controller = new NotificationController(port);

        SseEmitter result = controller.stream(7L);

        assertThat(capturedMemberId[0]).isEqualTo(7L);
        assertThat(result).isSameAs(expected);
    }
}
