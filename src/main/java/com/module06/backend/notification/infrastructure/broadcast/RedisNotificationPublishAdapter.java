package com.module06.backend.notification.infrastructure.broadcast;

import com.module06.backend.notification.application.port.out.NotificationEvent;
import com.module06.backend.notification.application.port.out.NotificationPublishPort;
import com.module06.backend.notification.infrastructure.sse.NotificationChannels;
import com.module06.backend.notification.infrastructure.sse.NotificationStreamRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/* comment.
    NotificationPublishPort의 실제 구현 — 다른 도메인(action, handover 등)이 이 포트로 넘긴 이벤트를
    Redis 알림 채널로 발행한다. 이 인스턴스를 포함한 모든 인스턴스의 NotificationRedisListener가 이
    메시지를 받아 각자 로컬 구독자에게 나눠준다(NotificationStreamRegistry 참고). cap의
    RedisCaptionBroadcastAdapter와 동일 패턴.
*/
@Component
public class RedisNotificationPublishAdapter implements NotificationPublishPort {

    private static final Logger log = LoggerFactory.getLogger(RedisNotificationPublishAdapter.class);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisNotificationPublishAdapter(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // 발행은 best-effort — Redis 장애가 호출한 도메인의 원래 작업(예: 액션 배정 저장)을 실패시키면
    // 안 되므로 여기서 예외를 삼킨다(cap의 publish()와 동일 근거).
    @Override
    public void publish(Long memberId, NotificationEvent event) {
        try {
            String json = objectMapper.writeValueAsString(
                    new NotificationStreamRegistry.NotificationMessage(memberId, event));
            redisTemplate.convertAndSend(NotificationChannels.NOTIFICATION, json);
        } catch (DataAccessException | JacksonException e) {
            log.warn("알림 발행 실패(memberId={}) — Redis 접근 오류, 호출자의 원래 작업은 이미 완료됨", memberId, e);
        }
    }
}
