package com.module06.backend.notification.infrastructure.sse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/* comment.
    알림 SSE 브로드캐스트용 Redis pub/sub 구독을 실제로 시작하는 곳 — RedisMessageListenerContainer는
    빈이 뜨자마자(지연 연결 아님) Redis에 연결해 구독을 건다. CI엔 Redis 서비스가 없어서 이 빈을 그냥
    두면 이 도메인과 무관한 테스트까지 포함해 전체 @SpringBootTest가 이 연결 시도에 걸린다
    (cap의 CaptionStreamRedisConfig와 동일 문제·동일 해법).

    ⚠️ @Profile("!test")가 아니라 프로퍼티 기반 조건을 쓴다 — 이 프로젝트는 테스트에서 실제 "test"
    프로파일을 활성화하지 않는다(src/test/resources/application.yaml을 기본 프로파일로 덮어쓰는 방식).
    src/test/resources/application.yaml에 notification.sse.enabled: false를 명시해 테스트에서만 끈다
    (운영/로컬은 matchIfMissing=true라 별도 설정 없이 켜짐).
*/
@Configuration
@ConditionalOnProperty(prefix = "notification.sse", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NotificationRedisConfig {

    @Bean
    public RedisMessageListenerContainer notificationStreamListenerContainer(
            RedisConnectionFactory connectionFactory, NotificationRedisListener listener) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(listener, new ChannelTopic(NotificationChannels.NOTIFICATION));
        return container;
    }
}
