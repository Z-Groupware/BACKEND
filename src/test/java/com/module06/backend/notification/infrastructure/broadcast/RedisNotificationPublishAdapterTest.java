package com.module06.backend.notification.infrastructure.broadcast;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.infrastructure.sse.NotificationChannels;

import tools.jackson.databind.json.JsonMapper;

/* 알림 발행이 지정한 Redis 채널로 나가는지, Redis 장애는 호출자에게 전파되지 않고 삼켜지는지(호출한
   도메인의 원래 작업을 실패시키면 안 되므로) 검증한다. */
@DisplayName("알림 Redis 발행 어댑터")
class RedisNotificationPublishAdapterTest {

    private final JsonMapper objectMapper = new JsonMapper();

    /* 정상 발행이면 알림 채널로 convertAndSend가 호출되는지 검증한다. */
    @Test
    @DisplayName("알림 채널로 발행한다")
    void publishesToNotificationChannel() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RedisNotificationPublishAdapter adapter = new RedisNotificationPublishAdapter(redisTemplate, objectMapper);

        adapter.publish(7L, new NotificationEvent("ACTION_ASSIGNED", "hi"));

        verify(redisTemplate).convertAndSend(eq(NotificationChannels.NOTIFICATION), anyString());
    }

    /* Redis 접근 오류가 나도 호출자에게 예외가 전파되지 않는지(best-effort) 검증한다. */
    @Test
    @DisplayName("Redis 장애가 나도 예외를 삼킨다")
    void swallowsRedisFailure() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        when(redisTemplate.convertAndSend(anyString(), any())).thenThrow(new QueryTimeoutException("redis down"));
        RedisNotificationPublishAdapter adapter = new RedisNotificationPublishAdapter(redisTemplate, objectMapper);

        assertThatCode(() -> adapter.publish(7L, new NotificationEvent("ACTION_ASSIGNED", "hi")))
                .doesNotThrowAnyException();
    }
}
