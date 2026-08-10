package com.module06.backend.notification.infrastructure.sse;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.Message;

import com.module06.backend.notification.application.port.out.NotificationEvent;

import tools.jackson.databind.json.JsonMapper;

/* Redis 알림 채널 메시지를 파싱해 레지스트리로 넘기는지, 파싱 실패 메시지는 삼키고 리스너를
   안 죽이는지 검증한다. */
@DisplayName("알림 Redis 리스너")
class NotificationRedisListenerTest {

    private final JsonMapper objectMapper = new JsonMapper();

    /* 정상 메시지를 파싱해 레지스트리에 정확한 memberId·event로 dispatch하는지 검증한다. */
    @Test
    @DisplayName("정상 메시지는 파싱해서 레지스트리로 넘긴다")
    void dispatchesParsedMessage() {
        NotificationStreamRegistry registry = mock(NotificationStreamRegistry.class);
        NotificationRedisListener listener = new NotificationRedisListener(registry, objectMapper);

        String json = objectMapper.writeValueAsString(
                new NotificationStreamRegistry.NotificationMessage(7L, new NotificationEvent("ACTION_ASSIGNED", "hi")));
        Message message = fakeMessage(json);

        listener.onMessage(message, null);

        verify(registry).dispatch(eq(7L), any(NotificationEvent.class));
    }

    /* 깨진 메시지는 예외를 삼키고(리스너 스레드를 안 죽이고), 레지스트리 호출도 안 하는지 검증한다. */
    @Test
    @DisplayName("깨진 메시지는 예외 없이 무시한다")
    void swallowsMalformedMessage() {
        NotificationStreamRegistry registry = mock(NotificationStreamRegistry.class);
        NotificationRedisListener listener = new NotificationRedisListener(registry, objectMapper);

        Message message = fakeMessage("이건 JSON이 아닙니다");

        assertThatCode(() -> listener.onMessage(message, null)).doesNotThrowAnyException();
        verify(registry, never()).dispatch(any(), any());
    }

    private Message fakeMessage(String body) {
        Message message = mock(Message.class);
        when(message.getBody()).thenReturn(body.getBytes(StandardCharsets.UTF_8));
        when(message.getChannel()).thenReturn(NotificationChannels.NOTIFICATION.getBytes(StandardCharsets.UTF_8));
        return message;
    }
}
