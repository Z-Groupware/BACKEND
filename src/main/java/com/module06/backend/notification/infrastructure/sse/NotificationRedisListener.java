package com.module06.backend.notification.infrastructure.sse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;

/* comment.
    Redis 알림 채널 구독자. 어느 인스턴스가 발행했든(자기 자신 포함) 메시지를 받아
    NotificationStreamRegistry.dispatch로 넘겨 "이 인스턴스에 붙은 로컬 구독자"에게만 전달한다
    (cap의 CaptionStreamRedisListener와 동일 패턴). 이 빈 자체는 순수 리스너 로직이라 테스트에서도
    안전하게 존재할 수 있다 — 실제로 Redis에 연결해 구독을 시작하는 건 NotificationRedisConfig의
    컨테이너(notification.sse.enabled 프로퍼티로 게이트).
*/
@Component
public class NotificationRedisListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationRedisListener.class);

    private final NotificationStreamRegistry registry;
    private final ObjectMapper objectMapper;

    public NotificationRedisListener(NotificationStreamRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        try {
            NotificationStreamRegistry.NotificationMessage parsed =
                    objectMapper.readValue(body, NotificationStreamRegistry.NotificationMessage.class);
            registry.dispatch(parsed.memberId(), parsed.event());
        } catch (Exception e) {
            // 파싱 실패한 메시지 하나 때문에 리스너 스레드가 죽으면 이후 모든 회원의 실시간 전달이 끊긴다.
            log.warn("알림 스트림 메시지 처리 실패", e);
        }
    }
}
